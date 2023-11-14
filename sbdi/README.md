# Biocache-service

## Setup
This service requires a rather elaborate directory and file structure in `/data/`:
```
mats@xps-13:/data$ tree biocache* cache offline logger-client
biocache
├── config
│   ├── applicationContext.xml -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/applicationContext.xml
│   ├── biocache-config.properties -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/biocache-config.properties
│   ├── download-csdm-email.html -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/download-csdm-email.html
│   ├── download-doi-email.html -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/download-doi-email.html
│   ├── download-doi-readme.html -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/download-doi-readme.html
│   ├── download-email.html -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/download-email.html
│   ├── download-readme.html -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/download-readme.html
│   ├── facets.json -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/facets.json
│   ├── groups.json -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/groups.json
│   ├── pipelines-field-config.json -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/pipelines-field-config.json
│   └── subgroups.json -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/subgroups.json
├── heatmap
└── tmp
biocache-delete
biocache-download
biocache-load
biocache-media
biocache-upload
cache
offline
└── exports
logger-client
└── config
   └── logger-client.properties -> /home/mats/src/biodiversitydata-se/biocache-service/sbdi/data/config/logger-client.properties
```

## Usage

Run locally:
```
make run
```

Build and run in Docker (using Tomcat):
```
make run-docker
```

Make a release. This will create a new tag and push it. A new Docker container will be built on Github.
```
mats@xps-13:~/src/biodiversitydata-se/biocache-service (master *)$ make release

Current version: 1.0.1. Enter the new version (or press Enter for 1.0.2): 
Updating to version 1.0.2
Tag 1.0.2 created and pushed.
```