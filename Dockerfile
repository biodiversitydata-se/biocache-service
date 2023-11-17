FROM tomcat:9.0-jdk11-temurin

ENV TZ=Europe/Stockholm

RUN mkdir -p  \
    /data/biocache/config \
    /data/biocache/heatmap \
    /data/biocache/tmp \
	/data/biocache-load \
	/data/biocache-media \
	/data/biocache-upload \
	/data/biocache-delete \
	/data/biocache-download/tmp \
    /data/cache \
    /data/offline/exports \
    /data/logger-client/config

COPY sbdi/data/config/*.json /data/biocache/config/
COPY sbdi/data/config/*.xml /data/biocache/config/
COPY sbdi/data/config/*.html /data/biocache/config/

COPY build/libs/biocache-service-*.war $CATALINA_HOME/webapps/ws.war
COPY sbdi/data/biocache-download.xml /usr/local/tomcat/conf/Catalina/localhost/biocache-download.xml

ENV DOCKERIZE_VERSION v0.7.0

RUN apt-get update \
    && apt-get install -y wget \
    && wget -O - https://github.com/jwilder/dockerize/releases/download/$DOCKERIZE_VERSION/dockerize-linux-amd64-$DOCKERIZE_VERSION.tar.gz | tar xzf - -C /usr/local/bin \
    && apt-get autoremove -yqq --purge wget && rm -rf /var/lib/apt/lists/*
