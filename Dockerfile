ARG APP_DIR=/opt/silo

FROM maven:3.6.3-openjdk-11-slim AS build
ARG APP_DIR
WORKDIR ${APP_DIR}

COPY . ./
RUN apt-get update && apt-get install -y \
    figlet \
    && rm -rf /var/lib/apt/lists/*
# Cache build dependencies (optional): --mount=type=cache,target=/root/.m2
RUN --mount=type=cache,target=/root/.m2 mvn -e -f pom.xml -DskipTests clean package \
    && echo "$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout=true)" > VERSION.txt \
    && figlet -f slant "SILO $(cat VERSION.txt)" > BANNER.txt \
    && echo "Image build date: $(date --iso-8601=seconds)" >> BANNER.txt

FROM openjdk:11-slim
ARG APP_DIR
WORKDIR ${APP_DIR}
COPY docker-entrypoint.sh ./
COPY --from=build ${APP_DIR}/*.txt ./resources/
COPY --from=build ${APP_DIR}/useCases/maryland/target/silo.jar ./silo.jar
ENV SILO_HOME=${APP_DIR} \
    SILO_INPUT=${APP_DIR}/data/input
#    SILO_OUTPUT=${APP_DIR}/data/output
RUN apt-get update && apt-get install -y \
    libfreetype6 \
    libfontconfig1 \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p ${SILO_INPUT}
#    && mkdir -p ${SILO_OUTPUT}
VOLUME ${APP_DIR}/data
RUN ["chmod", "+x", "./docker-entrypoint.sh"]
ENTRYPOINT ["./docker-entrypoint.sh", "java", "-jar", "silo.jar", "/opt/silo/data/input/test/scenarios/annapolis/javaFiles/siloMatsim_multiYear.properties", "/opt/silo/data/input/test/scenarios/annapolis/matsim_input/config.xml"]
#ENTRYPOINT ["./docker-entrypoint.sh", "java", "-cp", "silo.jar", "de.tum.bgu.msm.transportModel.matsim.SiloMatsimMultiYearTest"]
#ENTRYPOINT ["./docker-entrypoint.sh", "java", "-cp", "maryland:silo.jar", "de.tum.bgu.msm.run.SiloMstm", "/opt/silo/data/input/test/scenarios/annapolis/javaFiles/siloMatsim_multiYear.properties", "/opt/silo/data/input/test/scenarios/annapolis/matsim_input/config.xml"]