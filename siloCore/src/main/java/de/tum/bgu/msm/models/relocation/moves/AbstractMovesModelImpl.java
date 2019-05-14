package de.tum.bgu.msm.models.relocation.moves;

import com.google.common.collect.ConcurrentHashMultiset;
import com.google.common.collect.Iterables;

import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.accessibility.Accessibility;
import de.tum.bgu.msm.data.accessibility.CommutingTimeProbability;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.dwelling.RealEstateDataManagerImpl;
import de.tum.bgu.msm.data.geo.GeoData;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.household.HouseholdDataManager;
import de.tum.bgu.msm.data.household.HouseholdType;
import de.tum.bgu.msm.data.household.IncomeCategory;
import de.tum.bgu.msm.data.travelTimes.SkimTravelTimes;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.events.impls.household.MoveEvent;
import de.tum.bgu.msm.models.AbstractModel;
import de.tum.bgu.msm.models.transportModel.matsim.MatsimTravelTimes;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.util.concurrent.ConcurrentExecutor;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.commons.math3.util.Precision;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static de.tum.bgu.msm.data.dwelling.RealEstateUtils.RENT_CATEGORIES;

public abstract class AbstractMovesModelImpl extends AbstractModel implements MovesModel {

    protected final static Logger logger = Logger.getLogger(AbstractMovesModelImpl.class);

    protected final GeoData geoData;
    protected final Accessibility accessibility;
    protected final CommutingTimeProbability commutingTimeProbability;
    private final MovesStrategy strategy;

    private final Map<HouseholdType, Double> averageHousingSatisfaction = new ConcurrentHashMap<>();
    private final Map<Integer, Double> satisfactionByHousehold = new ConcurrentHashMap<>();


    public AbstractMovesModelImpl(DataContainer dataContainer, Properties properties, MovesStrategy strategy) {
        super(dataContainer, properties);
        this.geoData = dataContainer.getGeoData();
        this.accessibility = dataContainer.getAccessibility();
        this.commutingTimeProbability = dataContainer.getCommutingTimeProbability();
        this.strategy = strategy;
    }

    protected abstract double calculateHousingUtility(Household hh, Dwelling dwelling, TravelTimes travelTimes);

    protected abstract void calculateRegionalUtilities();

    protected abstract boolean isHouseholdEligibleToLiveHere(Household household, Dwelling dd);

    public abstract int searchForNewDwelling(Household household);

    @Override
    public void setup() {
    }

    @Override
    public void prepareYear(int year) {
        calculateRegionalUtilities();
        calculateAverageHousingUtility();
    }

    @Override
    public List<MoveEvent> getEventsForCurrentYear(int year) {
        final List<MoveEvent> events = new ArrayList<>();
        for (Household hh : dataContainer.getHouseholdDataManager().getHouseholds()) {
            events.add(new MoveEvent(hh.getId()));
        }
        return events;
    }

    @Override
    public void endYear(int year) {
    }

    /**
     * Simulates (a) if this household moves and (b) where this household moves
     */
    @Override
    public boolean handleEvent(MoveEvent event) {

        int hhId = event.getHouseholdId();
        Household household = dataContainer.getHouseholdDataManager().getHouseholdFromId(hhId);
        if (household == null) {
            // Household does not exist anymore
            return false;
        }

        // Step 1: Consider relocation if household is not very satisfied or if household income exceed restriction for low-income dwelling
        if (!moveOrNot(household)) {
            return false;
        }

        // Step 2: Choose new dwelling
        int idNewDD = searchForNewDwelling(household);

        if (idNewDD > 0) {

            // Step 3: Move household
            dataContainer.getHouseholdDataManager().saveHouseholdMemento(household);
            moveHousehold(household, household.getDwellingId(), idNewDD);
            if (hhId == SiloUtil.trackHh) {
                SiloUtil.trackWriter.println("Household " + hhId + " has moved to dwelling " +
                        household.getDwellingId());
            }
            return true;
        } else {
            if (hhId == SiloUtil.trackHh) {
                SiloUtil.trackWriter.println("Household " + hhId + " intended to move but " +
                        "could not find an adequate dwelling.");
            }
            return false;
        }
    }

    protected double convertPriceToUtility(int price, HouseholdType ht) {
        IncomeCategory incCategory = ht.getIncomeCategory();
        Map<Integer, Float> shares = dataContainer.getRealEstateDataManager().getRentPaymentsForIncomeGroup(incCategory);
        // 25 rent categories are defined as <rent/200>, see RealEstateDataManager
        int priceCategory = (int) (price / 200f + 0.5);
        priceCategory = Math.min(priceCategory, RENT_CATEGORIES);
        double util = 0;
        for (int i = 0; i <= priceCategory; i++) {
            util += shares.get(i);
        }
        // invert utility, as lower price has higher utility
        return Math.max(0., 1f - util);
    }

    protected double convertPriceToUtility(int price, IncomeCategory incCategory) {

        Map<Integer, Float> shares = dataContainer.getRealEstateDataManager().getRentPaymentsForIncomeGroup(incCategory);
        // 25 rent categories are defined as <rent/200>, see RealEstateDataManager
        int priceCategory = (int) (price / 200f);
        priceCategory = Math.min(priceCategory, RENT_CATEGORIES);
        double util = 0;
        for (int i = 0; i <= priceCategory; i++) {
            util += shares.get(i);
        }
        // invert utility, as lower price has higher utility
        return Math.max(0, 1.f - util);
    }

    protected double convertQualityToUtility(int quality) {
        return (float) quality / (float) properties.main.qualityLevels;
    }

    protected double convertAreaToUtility(int area) {
        return (float) area / (float) RealEstateDataManagerImpl.largestNoBedrooms;
    }

    protected double convertAccessToUtility(double accessibility) {
        return accessibility / 100f;
    }

    protected Map<Integer, Double> calculateRegionalPrices() {
        final Map<Integer, Zone> zones = geoData.getZones();
        final Map<Integer, List<Dwelling>> dwellingsByRegion =
                dataContainer.getRealEstateDataManager().getDwellings().parallelStream().collect(Collectors.groupingByConcurrent(d ->
                        zones.get(d.getZoneId()).getRegion().getId()));
        final Map<Integer, Double> rentsByRegion = dwellingsByRegion.entrySet().parallelStream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().stream().mapToDouble(Dwelling::getPrice).average().getAsDouble()));
        return rentsByRegion;
    }

    protected boolean moveOrNot(Household household) {
        HouseholdType hhType = household.getHouseholdType();
        Dwelling dd = dataContainer.getRealEstateDataManager().getDwelling(household.getDwellingId());
        if (!isHouseholdEligibleToLiveHere(household, dd)) {
            return true;
        }
        final double currentUtil = satisfactionByHousehold.get(household.getId());
        final double avgSatisfaction = averageHousingSatisfaction.getOrDefault(hhType, currentUtil);

        final double prop = strategy.getMovingProbability(avgSatisfaction, currentUtil);
        return SiloUtil.getRandomNumberAsDouble() <= prop;
    }


    private void calculateAverageHousingUtility() {
        logger.info("Evaluate average housing utility.");
        HouseholdDataManager householdDataManager = dataContainer.getHouseholdDataManager();
        logger.info("Number of households = " + householdDataManager.getHouseholds().size());

        averageHousingSatisfaction.replaceAll((householdType, aDouble) -> 0.);
        satisfactionByHousehold.clear();

        final Collection<Household> households = householdDataManager.getHouseholds();
        final int partitionSize = (int) ((double) households.size() / (Properties.get().main.numberOfThreads)) + 1;
        logger.info("Intended size of all of partititons = " + partitionSize);
        Iterable<List<Household>> partitions = Iterables.partition(households, partitionSize);
        ConcurrentExecutor<Void> executor = ConcurrentExecutor.fixedPoolService(Properties.get().main.numberOfThreads);
        ConcurrentHashMultiset<HouseholdType> hhByType = ConcurrentHashMultiset.create();

        for (final List<Household> partition : partitions) {
            logger.info("Size of partititon = " + partition.size());
            executor.addTaskToQueue(() -> {
                try {
                    TravelTimes travelTimes = dataContainer.getTravelTimes().duplicate();
                    for (Household hh : partition) {
                        final HouseholdType householdType = hh.getHouseholdType();
                        hhByType.add(householdType);
                        Dwelling dd = dataContainer.getRealEstateDataManager().getDwelling(hh.getDwellingId());
                        final double util = calculateHousingUtility(hh, dd, travelTimes);
                        satisfactionByHousehold.put(hh.getId(), util);
                        averageHousingSatisfaction.merge(householdType, util, (oldUtil, newUtil) -> oldUtil + newUtil);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                logger.warn("Finished thread");
                return null;
            });
        }
        executor.execute();

        averageHousingSatisfaction.replaceAll((householdType, satisfaction) ->
                Precision.round(satisfaction / (1. * hhByType.count(householdType)), 5));
    }

    public void moveHousehold(Household hh, int idOldDD, int idNewDD) {
        // if this household had a dwelling in this study area before, vacate old dwelling
        if (idOldDD > 0) {
            dataContainer.getRealEstateDataManager().vacateDwelling(idOldDD);
        }
        dataContainer.getRealEstateDataManager().removeDwellingFromVacancyList(idNewDD);
        dataContainer.getRealEstateDataManager().getDwelling(idNewDD).setResidentID(hh.getId());
        hh.setDwelling(idNewDD);
        if (hh.getId() == SiloUtil.trackHh) {
            SiloUtil.trackWriter.println("Household " +
                    hh.getId() + " moved from dwelling " + idOldDD + " to dwelling " + idNewDD + ".");
        }
    }
}