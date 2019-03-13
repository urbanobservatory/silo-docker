package de.tum.bgu.msm.models.realEstate;

import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.data.RealEstateDataImpl;
import de.tum.bgu.msm.data.dwelling.Dwelling;
import de.tum.bgu.msm.events.impls.realEstate.RenovationEvent;
import de.tum.bgu.msm.models.AbstractModel;
import de.tum.bgu.msm.models.EventModel;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Simulates renovation and deterioration of dwellings
 * Author: Rolf Moeckel, PB Albuquerque
 * Created on 7 January 2010 in Rhede
 **/
public class RenovationModel extends AbstractModel implements EventModel<RenovationEvent> {

    private double[][] renovationProbability;
    private final Reader reader;

    public RenovationModel(DataContainer dataContainer, Properties properties, InputStream inputStream) {
        super(dataContainer, properties);
        this.reader = new InputStreamReader(inputStream);
    }

    @Override
    public void setup() {
//        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("RenovationCalc"));
        RenovationJSCalculator renovationCalculator = new RenovationJSCalculator(reader);

        renovationProbability = new double[properties.main.qualityLevels][5];
        for (int oldQual = 0; oldQual < properties.main.qualityLevels; oldQual++) {
            for (int alternative = 0; alternative < 5; alternative++) {
                renovationProbability[oldQual][alternative] = renovationCalculator.calculateRenovationProbability(oldQual + 1, alternative + 1);
            }
        }
    }

    @Override
    public void prepareYear(int year) {}

    @Override
    public Collection<RenovationEvent> getEventsForCurrentYear(int year) {
        final List<RenovationEvent> events = new ArrayList<>();
        for (Dwelling dwelling : dataContainer.getRealEstateData().getDwellings()) {
            events.add(new RenovationEvent(dwelling.getId()));
        }
        return events;
    }

    @Override
    public boolean handleEvent(RenovationEvent event) {

        //check if dwelling is renovated or deteriorates
        final RealEstateDataImpl realEstateData = dataContainer.getRealEstateData();
        Dwelling dd = realEstateData.getDwelling(event.getDwellingId());
        if (dd != null) {
            int currentQuality = dd.getQuality();
            int selected = SiloUtil.select(getProbabilities(currentQuality));

            if (selected != 2) {
                realEstateData.dwellingsByQuality[currentQuality - 1] -= 1;
            }
            switch (selected) {
                case (0): {
                    realEstateData.dwellingsByQuality[currentQuality - 1 - 2] += 1;
                    dd.setQuality(currentQuality - 2);
                    break;
                }
                case (1): {
                    realEstateData.dwellingsByQuality[currentQuality - 1 - 1] += 1;
                    dd.setQuality(currentQuality - 1);
                    break;
                }
                case (3): {
                    realEstateData.dwellingsByQuality[currentQuality - 1 + 1] += 1;
                    dd.setQuality(currentQuality + 1);
                    break;
                }
                case (4): {
                    realEstateData.dwellingsByQuality[currentQuality - 1 + 2] += 1;
                    dd.setQuality(currentQuality + 2);
                    break;
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public void endYear(int year) {

    }

    @Override
    public void endSimulation() {

    }

    private double[] getProbabilities(int currentQual) {
        // return probabilities to upgrade or deteriorate based on current quality of dwelling and average
        // quality of all dwellings
        double[] currentShare = dataContainer.getRealEstateData().getCurrentQualShares();
        // if share of certain quality level is currently 0, set it to very small number to ensure model keeps working
        for (int i = 0; i < currentShare.length; i++) if (currentShare[i] == 0) currentShare[i] = 0.01d;
        double[] initialShare = dataContainer.getRealEstateData().getInitialQualShares();
        for (int i = 0; i < initialShare.length; i++) if (initialShare[i] == 0) initialShare[i] = 0.01d;
        double[] probs = new double[5];
        for (int i = 0; i < probs.length; i++) {
            int potentialNewQual = currentQual + i - 2;  // translate into new quality level this alternative would generate
            double ratio;
            if (potentialNewQual >= 1 && potentialNewQual <= 4)
                ratio = initialShare[potentialNewQual - 1] / currentShare[potentialNewQual - 1];
            else ratio = 0.;
            if (i <= 1) {
                probs[i] = renovationProbability[currentQual - 1][i] * ratio;
            } else if (i == 2) {
                probs[i] = renovationProbability[currentQual - 1][i];
            } else probs[i] = renovationProbability[currentQual - 1][i] * ratio;
        }
        return probs;
    }
}

