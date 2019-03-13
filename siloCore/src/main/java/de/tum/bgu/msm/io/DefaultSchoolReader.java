package de.tum.bgu.msm.io;

import de.tum.bgu.msm.Implementation;
import de.tum.bgu.msm.data.SchoolDataImpl;
import de.tum.bgu.msm.data.school.School;
import de.tum.bgu.msm.data.school.SchoolFactory;
import de.tum.bgu.msm.data.school.SchoolUtils;
import de.tum.bgu.msm.properties.Properties;
import de.tum.bgu.msm.utils.SiloUtil;
import org.apache.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class DefaultSchoolReader implements SchoolReader {

    private final static Logger logger = Logger.getLogger(DefaultSchoolReader.class);
    private final SchoolDataImpl schoolData;

    public DefaultSchoolReader(SchoolDataImpl schoolData) {
        this.schoolData = schoolData;
    }

    @Override
    public void readData(String fileName) {

        logger.info("Reading school micro data from ascii file");
        SchoolFactory factory = SchoolUtils.getFactory();
        String recString = "";
        int recCount = 0;

        try {
            BufferedReader in = new BufferedReader(new FileReader(fileName));
            recString = in.readLine();

            // read header
            String[] header = recString.split(",");
            int posId = SiloUtil.findPositionInArray("id", header);
            int posZone = SiloUtil.findPositionInArray("zone", header);
            int posCapacity = SiloUtil.findPositionInArray("capacity", header);
            int posOccupancy = SiloUtil.findPositionInArray("occupancy", header);
            int posType = SiloUtil.findPositionInArray("type", header);

            int posCoordX = -1;
            int posCoordY = -1;
            if(Properties.get().main.implementation == Implementation.MUNICH) {
                posCoordX = SiloUtil.findPositionInArray("CoordX", header);
                posCoordY = SiloUtil.findPositionInArray("CoordY", header);
            }

            // read line
            while ((recString = in.readLine()) != null) {
                recCount++;
                String[] lineElements = recString.split(",");
                int id = Integer.parseInt(lineElements[posId]);
                int zoneId = Integer.parseInt(lineElements[posZone]);
                int type = Integer.parseInt(lineElements[posType]);
                int capacity = Integer.parseInt(lineElements[posOccupancy]);
                int occupancy = Integer.parseInt(lineElements[posCapacity]);

                Coordinate coordinate = null;
                //TODO: remove it when we implement interface
                if (Properties.get().main.implementation == Implementation.MUNICH) {
                    coordinate = new Coordinate(Double.parseDouble(lineElements[posCoordX]), Double.parseDouble(lineElements[posCoordY]));
                }

                School ss = factory.createSchool(id, type, capacity, occupancy, coordinate,zoneId);
                schoolData.addSchool(ss);
//Qin???
//                if (id == SiloUtil.trackSs) {
//                    SiloUtil.trackWriter.println("Read school with following attributes from " + fileName);
//                    SiloUtil.trackWriter.println(schools.get(id).toString());
//                }
            }


        } catch (IOException e) {
            logger.fatal("IO Exception caught reading synpop school file: " + fileName);
            logger.fatal("recCount = " + recCount + ", recString = <" + recString + ">");
        }
        logger.info("Finished reading " + recCount + " schools.");
    }

}
