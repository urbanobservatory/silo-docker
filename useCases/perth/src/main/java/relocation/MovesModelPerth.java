package relocation;

/*
 * Implementation of the MovesModel Interface for the Munich implementation
 * @author Rolf Moeckel
 * Date: 20 May 2017, near Greenland in an altitude of 35,000 feet
*/

import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.data.Region;
import de.tum.bgu.msm.data.Zone;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.data.dwelling.RealEstateDataManager;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.household.HouseholdType;
import de.tum.bgu.msm.data.household.HouseholdUtil;
import de.tum.bgu.msm.data.household.IncomeCategory;
import de.tum.bgu.msm.data.job.Job;
import de.tum.bgu.msm.data.job.JobDataManager;
import de.tum.bgu.msm.data.person.Occupation;
import de.tum.bgu.msm.data.person.Person;
import de.tum.bgu.msm.data.travelTimes.TravelTimes;
import de.tum.bgu.msm.models.relocation.moves.AbstractMovesModelImpl;
import de.tum.bgu.msm.models.relocation.moves.DwellingProbabilityStrategy;
import de.tum.bgu.msm.models.relocation.moves.MovesStrategy;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.util.matrices.IndexedDoubleMatrix1D;
import de.tum.bgu.msm.utils.SiloUtil;
import org.matsim.api.core.v01.TransportMode;

import java.util.*;

public class MovesModelPerth extends AbstractMovesModelImpl {

    private final DwellingUtilityStrategy dwellingUtilityStrategy;
    private final DwellingProbabilityStrategy dwellingProbabilityStrategy;
    private final SelectRegionStrategy selectRegionStrategy;
    private EnumMap<IncomeCategory, Map<Integer, Double>> utilityByIncomeByRegion = new EnumMap<>(IncomeCategory.class) ;

    private IndexedDoubleMatrix1D hhByRegion;


    public MovesModelPerth(DataContainer dataContainer, Properties properties, MovesStrategy movesStrategy,
                           DwellingUtilityStrategy dwellingUtilityStrategy,
                           DwellingProbabilityStrategy dwellingProbabilityStrategy,
                           SelectRegionStrategy selectRegionStrategy) {
        super(dataContainer, properties, movesStrategy );
        this.dwellingUtilityStrategy = dwellingUtilityStrategy;
        this.dwellingProbabilityStrategy = dwellingProbabilityStrategy;
        this.selectRegionStrategy = selectRegionStrategy;
    }

    @Override
    public void setup() {
        hhByRegion = new IndexedDoubleMatrix1D(geoData.getRegions().values());
        super.setup();
    }

    @Override
    public void endYear(int year) {}

    @Override
    public void endSimulation() {}


    private void calculateShareOfForeignersByZoneAndRegion() {
        final IndexedDoubleMatrix1D hhByZone = new IndexedDoubleMatrix1D(geoData.getZones().values());
        hhByRegion.assign(0);
        for (Household hh: dataContainer.getHouseholdDataManager().getHouseholds()) {
            int zone;
            Dwelling dwelling = dataContainer.getRealEstateDataManager().getDwelling(hh.getDwellingId());
            if (dwelling != null) {
                zone = dwelling.getZoneId();
            } else {
                logger.warn("Household " + hh.getId() + " refers to non-existing dwelling "
                        + hh.getDwellingId() +". Should not happen!");
                continue;
            }
            final int region = geoData.getZones().get(zone).getRegion().getId();
            hhByZone.setIndexed(zone, hhByZone.getIndexed(zone) + 1);
            hhByRegion.setIndexed(region, hhByRegion.getIndexed(region) + 1);

        }

    }

//    private double convertDistToWorkToUtil (Household hh, int homeZone) {
//        // convert distance to work and school to utility
//        double util = 1;
//        for (Person p: hh.getPersons()) {
//            if (p.getOccupation() == 1 && p.getWorkplace() != -2) {
//                int workZone = Job.getJobFromId(p.getWorkplace()).getZone();
//                int travelTime = (int) SiloUtil.rounder(siloModelContainer.getAcc().getAutoTravelTime(homeZone, workZone),0);
//                util = util * siloModelContainer.getAcc().getCommutingTimeProbability(travelTime);
//            }
//        }
//        return util;
//    }


//    private double convertTravelCostsToUtility (Household hh, int homeZone) {
//        // convert travel costs to utility
//        double util = 1;
//        float workTravelCostsGasoline = 0;
//        for (Person p: hh.getPersons()) if (p.getOccupation() == 1 && p.getWorkplace() != -2) {
//            int workZone = Job.getJobFromId(p.getWorkplace()).getZone();
//            // yearly commute costs with 251 work days over 12 months, doubled to account for return trip
//            workTravelCostsGasoline += siloModelContainer.getAcc().getTravelCosts(homeZone, workZone) * 251f * 2f;
//        }
//        // todo: Create more plausible utilities
//        // Assumptions: Transportation costs are 5.9-times higher than expenditures for gasoline (https://www.census.gov/compendia/statab/2012/tables/12s0688.xls)
//        // Households spend 19% of their income on transportation, and 70% thereof is not
//        // work-related (but HBS, HBO, NHB, etc. trips)
//        float travelCosts = workTravelCostsGasoline * 5.9f + (hh.getHhIncome() * 0.19f * 0.7f);
//        if (travelCosts > (hh.getHhIncome() * 0.19f)) util = 0.5;
//        if (travelCosts > (hh.getHhIncome() * 0.25f)) util = 0.4;
//        if (travelCosts > (hh.getHhIncome() * 0.40f)) util = 0.2;
//        if (travelCosts > (hh.getHhIncome() * 0.50f)) util = 0.0;
//        return util;
//    }


    @Override
    public void calculateRegionalUtilities() {
        logger.info("Calculating regional utilities");
        calculateShareOfForeignersByZoneAndRegion();
        final Map<Integer, Double> rentsByRegion = calculateRegionalPrices();
        for (IncomeCategory incomeCategory: IncomeCategory.values()) {
                Map<Integer, Double> utilityByRegion = new HashMap<>();
                for (Region region : geoData.getRegions().values()){
                    final int averageRegionalRent = rentsByRegion.get(region.getId()).intValue();
                    final float regAcc = (float) convertAccessToUtility(accessibility.getRegionalAccessibility(region));
                    float priceUtil = (float) convertPriceToUtility(averageRegionalRent, incomeCategory);
                    final double value = selectRegionStrategy.calculateSelectRegionProbability(incomeCategory,
                             priceUtil, regAcc, 0);
                    utilityByRegion.put(region.getId(),
                            value);
                }

            utilityByIncomeByRegion.put(incomeCategory, utilityByRegion);
        }

    }

    @Override
    protected boolean isHouseholdEligibleToLiveHere(Household household, Dwelling dd) {
        return true;
    }

    private Map<Integer, Double> getUtilitiesByRegionForThisHousehold(HouseholdType ht, Collection<Zone> workZones){
        Map<Integer, Double> utilitiesForThisHousheold
                = new HashMap<>(utilityByIncomeByRegion.get(ht.getIncomeCategory()));

        for(Region region : geoData.getRegions().values()){
            double thisRegionFactor = 1;
            if (workZones != null) {
                for (Zone workZone : workZones) {
                    int timeFromZoneToRegion = (int) dataContainer.getTravelTimes().getTravelTimeToRegion(
                    		workZone, region, properties.transportModel.peakHour_s, TransportMode.car);
                    thisRegionFactor = thisRegionFactor * commutingTimeProbability.getCommutingTimeProbability(timeFromZoneToRegion);
                }
            }
            utilitiesForThisHousheold.put(region.getId(),utilitiesForThisHousheold.get(region.getId())*thisRegionFactor);
        }
        return utilitiesForThisHousheold;
    }

    @Override
    public int searchForNewDwelling(Household household) {
        // search alternative dwellings

        // data preparation
        int householdIncome = 0;
        Map<Person, Zone> workerZonesForThisHousehold = new HashMap<>();
        JobDataManager jobDataManager = dataContainer.getJobDataManager();
        RealEstateDataManager realEstateDataManager = dataContainer.getRealEstateDataManager();
        for (Person pp: household.getPersons().values()) {
            if (pp.getOccupation() == Occupation.EMPLOYED && pp.getJobId() != -2) {
                final Job job = jobDataManager.getJobFromId(pp.getJobId());
                if(job != null) {
                    Zone workZone = geoData.getZones().get(job.getZoneId());
                    workerZonesForThisHousehold.put(pp, workZone);
                    householdIncome += pp.getIncome();
                }
            }
        }

        HouseholdType ht = HouseholdUtil.defineHouseholdType(household);

        // Step 1: select region
        Map<Integer, Double> regionUtilitiesForThisHousehold  = new HashMap<>();
        regionUtilitiesForThisHousehold.putAll(getUtilitiesByRegionForThisHousehold(ht,workerZonesForThisHousehold.values()));

        // todo: adjust probabilities to make that households tend to move shorter distances (dist to work is already represented)
        String normalizer = "powerOfPopulation";
        int totalVacantDd = 0;
        for (int region: geoData.getRegions().keySet()) {
            totalVacantDd += realEstateDataManager.getNumberOfVacantDDinRegion(region);
        }
        for (int region : regionUtilitiesForThisHousehold.keySet()){
            switch (normalizer) {
                case ("vacDd"): {
                    // Multiply utility of every region by number of vacant dwellings to steer households towards available dwellings
                    // use number of vacant dwellings to calculate attractivity of region
                    regionUtilitiesForThisHousehold.put(region, regionUtilitiesForThisHousehold.get(region) * (float) realEstateDataManager.getNumberOfVacantDDinRegion(region));
                } case ("shareVacDd"): {
                    // use share of empty dwellings to calculate attractivity of region
                    regionUtilitiesForThisHousehold.put(region, regionUtilitiesForThisHousehold.get(region) * ((float) realEstateDataManager.getNumberOfVacantDDinRegion(region) / (float) totalVacantDd));
                } case ("dampenedVacRate"): {
                    double x = (double) realEstateDataManager.getNumberOfVacantDDinRegion(region) /
                            (double) realEstateDataManager.getNumberOfVacantDDinRegion(region) * 100d;  // % vacancy
                    double y = 1.4186E-03 * Math.pow(x, 3) - 6.7846E-02 * Math.pow(x, 2) + 1.0292 * x + 4.5485E-03;
                    y = Math.min(5d, y);                                                // % vacancy assumed to be ready to move in
                    regionUtilitiesForThisHousehold.put(region, regionUtilitiesForThisHousehold.get(region) * (y / 100d * realEstateDataManager.getNumberOfVacantDDinRegion(region)));
                    if (realEstateDataManager.getNumberOfVacantDDinRegion(region) < 1) {
                        regionUtilitiesForThisHousehold.put(region, 0D);
                    }
                } case ("population"): {
                    regionUtilitiesForThisHousehold.put(region, regionUtilitiesForThisHousehold.get(region) * hhByRegion.getIndexed(region));
                } case ("noNormalization"): {
                    // do nothing
                }case ("powerOfPopulation"): {
                    regionUtilitiesForThisHousehold.put(region, regionUtilitiesForThisHousehold.get(region) * Math.pow(hhByRegion.getIndexed(region),0.5));
                }
            }
        }


        int selectedRegionId;
        final double sum = regionUtilitiesForThisHousehold.values().stream().mapToDouble(i -> i).sum();
        if (sum == 0) {
            return -1;
        } else if(sum < 0) {
            System.out.println("sum is negative!");
            return -1;
        } else {
            selectedRegionId = SiloUtil.select(regionUtilitiesForThisHousehold);
        }

        //todo debugging
//        for(Person worker : workerZonesForThisHousehold.keySet()){
//            pw.println(year + "," +
//                    worker.getHh().getZoneId() + "," +
//                    worker.getZoneId() + "," +
//                    dataContainer.getJobDataManager().getJobFromId(worker.getWorkplace()).getZone() + "," +
//                    selectedRegionId  + "," +
//                    accessibility.getMinTravelTimeFromZoneToRegion(dataContainer.getJobDataManager().getJobFromId(worker.getWorkplace()).getZone(), selectedRegionId));
//        }



        // Step 2: select vacant dwelling in selected region
        List<Dwelling> vacantDwellings = realEstateDataManager.getListOfVacantDwellingsInRegion(selectedRegionId);
        Map<Dwelling, Double> dwellingProbs = new LinkedHashMap<>();
        double sumProbs = 0.;
        // No household will evaluate more than 20 dwellings
        int maxNumberOfDwellings = Math.min(20, vacantDwellings.size());
        float factor = ((float) maxNumberOfDwellings / (float) vacantDwellings.size());
        for (Dwelling dwelling: vacantDwellings) {
            if (SiloUtil.getRandomNumberAsFloat() > factor) {
                continue;
            }
            double util = calculateHousingUtility(household, dwelling, dataContainer.getTravelTimes());
            double probability = dwellingProbabilityStrategy.calculateSelectDwellingProbability(util);
            sumProbs =+ probability;
            dwellingProbs.put(dwelling, probability);
        }
        if (sumProbs == 0) {
            // could not find dwelling that fits restrictions
            return -1;
        }
        Dwelling selected = SiloUtil.select(dwellingProbs, sumProbs);
        return selected.getId();
    }

    @Override
    protected double calculateHousingUtility(Household hh, Dwelling dd, TravelTimes travelTimes) {
        if(dd == null) {
            logger.warn("Household " + hh.getId() + " has no dwelling. Setting housing satisfaction to 0");
            return 0;
        }
        double ddQualityUtility = convertQualityToUtility(dd.getQuality());
        double ddSizeUtility = convertAreaToUtility(dd.getBedrooms());
        double ddAutoAccessibilityUtility = convertAccessToUtility(accessibility.getAutoAccessibilityForZone(geoData.getZones().get(dd.getZoneId())));
        HouseholdType ht = hh.getHouseholdType();
        double ddPriceUtility = convertPriceToUtility(dd.getPrice(), ht);


        //currently this is re-filtering persons to find workers (it was done previously in select region)
        // This way looks more flexible to account for other trips, such as education, though.

        double travelCostUtility = 1; //do not have effect at the moment;

        Map<Person, Job> jobsForThisHousehold = new HashMap<>();
        JobDataManager jobDataManager = dataContainer.getJobDataManager();
        for (Person pp: hh.getPersons().values()) {
            if (pp.getOccupation() == Occupation.EMPLOYED && pp.getJobId() != -2) {
                final Job job = jobDataManager.getJobFromId(pp.getJobId());
                if(job != null) {
                    jobsForThisHousehold.put(pp, job);
                }
            }
        }
        double workDistanceUtility = 1;
        for (Job workLocation : jobsForThisHousehold.values()){
        	int expectedCommuteTime = (int) travelTimes.getTravelTime(dd, workLocation, properties.transportModel.peakHour_s, TransportMode.car);
            double factorForThisZone = commutingTimeProbability.getCommutingTimeProbability(Math.max(1, expectedCommuteTime));
            workDistanceUtility *= factorForThisZone;
        }

        return dwellingUtilityStrategy.calculateSelectDwellingUtility(ht, ddSizeUtility, ddPriceUtility,
                ddQualityUtility, ddAutoAccessibilityUtility,
                0, workDistanceUtility);
    }
}
