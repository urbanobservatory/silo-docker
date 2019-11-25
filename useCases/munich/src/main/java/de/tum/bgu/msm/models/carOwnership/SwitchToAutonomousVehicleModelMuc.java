package de.tum.bgu.msm.models.carOwnership;

import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.data.household.HouseholdMuc;
import de.tum.bgu.msm.data.household.Household;
import de.tum.bgu.msm.data.household.HouseholdDataManager;
import de.tum.bgu.msm.data.household.HouseholdUtil;
import de.tum.bgu.msm.models.AbstractModel;
import de.tum.bgu.msm.models.ModelUpdateListener;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Created by matthewokrah on 26/06/2018.
 */
public class SwitchToAutonomousVehicleModelMuc extends AbstractModel implements ModelUpdateListener {

    private final static Logger logger = Logger.getLogger(SwitchToAutonomousVehicleModelMuc.class);
    private SwitchToAutonomousVehicleJSCalculatorMuc calculator;
    private final Reader reader;

    /**
     * this variable stores a summary for print out purposes
     */
    private Map<String, Integer> summary = new HashMap<>();

    public SwitchToAutonomousVehicleModelMuc(DataContainer dataContainer, Properties properties, InputStream inputStream, Random rnd) {
        super(dataContainer, properties, rnd);
        this.reader = new InputStreamReader(inputStream);
    }

    @Override
    public void setup() {
//        Reader reader = new InputStreamReader(this.getClass().getResourceAsStream("SwitchToAutonomousVehicleCalc"));
        calculator = new SwitchToAutonomousVehicleJSCalculatorMuc(reader);
    }

    @Override
    public void prepareYear(int year) {
        summary.clear();

    }

    @Override
    public void endYear(int year) {
        switchToAV(year);

    }

    @Override
    public void endSimulation() {

    }

    private void switchToAV(int year) {

        int event_counter = 0;
        int autos_counter = 0;
        int av_counter = 0;
        HouseholdDataManager householdDataManager = dataContainer.getHouseholdDataManager();

        // return HashMap<Household, ArrayOfHouseholdAttributes>. These are the households eligible for switching
        // to autonomous cars. currently income is the only household attribute used but room is left for additional
        // attributes in the future
        for (Household hh : householdDataManager.getHouseholds()) {
            int autonomous = ((HouseholdMuc) hh).getAutonomous();
            av_counter += autonomous;
            autos_counter += hh.getAutos();
            if (hh.getAutos() > autonomous) {
                int income = HouseholdUtil.getAnnualHhIncome(hh);
                double[] prob = calculator.calculate(income, year);
                int action = SiloUtil.select(prob, random);
                if (action == 1) {
                    ((HouseholdMuc)hh).setAutonomous(autonomous + 1);
                    event_counter++;
                }
            }
        }


        int hh = dataContainer.getHouseholdDataManager().getHouseholds().size();
        summary.put("hh", hh);
        summary.put("autos", autos_counter);
        summary.put("avs", av_counter);
        summary.put("events", event_counter);


        //todo reconsider to print out model results and how to pass them to the ResultsMonitor
        logger.info(" Simulated household switched to AV " + event_counter + " (" +
                SiloUtil.rounder((100. * event_counter / hh), 0) + "% of hh)");
    }

    public Map<String, Integer> getSummaryForThisYear() {
        return summary;
    }
}
