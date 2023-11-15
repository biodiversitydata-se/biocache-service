run:
	docker compose up cassandra solr --detach
	./gradlew bootRun

# Change cassandra and solr connections in biocache-config.properties for this to work
# Replace localhost with cassandra and solr respectively
# Also, the service may fail on startup if cassandra isn't ready. Just restart the service if that happens.
run-docker:
	./gradlew clean build -x test
	docker compose build --no-cache
	docker compose up --detach

release:
	../sbdi-install/utils/make-release.sh
