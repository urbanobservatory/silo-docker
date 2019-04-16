package de.tum.bgu.msm.run;

import de.tum.bgu.msm.SiloModel;
import de.tum.bgu.msm.container.DataContainer;
import de.tum.bgu.msm.container.ModelContainer;
import de.tum.bgu.msm.io.output.DefaultResultsMonitor;
import de.tum.bgu.msm.io.output.ResultsMonitor;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

public class SiloCT {

    private final static Logger logger = Logger.getLogger(SiloCT.class);

    public static void main(String[] args) {

        Properties properties = SiloUtil.siloInitialization(args[0]);

        Config config = null;
        if (args.length > 1 && args[1] != null) {
            config = ConfigUtils.loadConfig(args[1]);
        }
        logger.info("Starting SILO land use model for Cape Town");
        DataContainer dataContainer = DataBuilderCapeTown.getModelDataForCapeTown(properties);
        DataBuilderCapeTown.read(properties, dataContainer);
        ModelContainer modelContainer = ModelBuilderCapeTown.getModelContainerForCapeTown(
                dataContainer, properties, config);
        ResultsMonitor resultsMonitor = new DefaultResultsMonitor(dataContainer, properties);
        SiloModel model = new SiloModel(properties, dataContainer, modelContainer, resultsMonitor);
        model.runModel();
        logger.info("Finished SILO.");
    }
}