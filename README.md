[![build](https://github.com/apprme/booking/actions/workflows/build.yml/badge.svg)](https://github.com/apprme/booking/actions/workflows/build.yml)

# booking-service

Code challenge featuring event-driven architecture with Akka — implementation of the Actor Model on the JVM.

## Running

### IntelliJ IDEA

* Make sure you have PostgreSQL server running at coordinates specified in `persistence.conf`. You can launch it in
  docker by executing `docker-compose up postgres` in the `docker` directory
  (have docker-engine and docker-compose installed).

* The project comes with Run Configurations for IntelliJ IDEA. Start any of the `node1`, `node2`, or `node3` (or any
  combination of them). HTTP endpoint will be available on ports 8081, 8082, and 8083 respectively.
  
### docker-compose

Alternatively, you can use docker-compose to start three nodes with NGINX as a simple load-balancer in round-robin
configuration.

First, build the project with `./gradlew build` and then execute `docker-compose up` in the `docker` directory.
NGINX will be available at `http://localhost:9000` 

### Integration tests

For tests that require third-party software running alongside the application (for example, PostgreSQL)
[testcontainers](https://www.testcontainers.org) is used. Before running integration tests make sure 
you have docker-engine installed and running.

You can run integration tests either from your IDE or, alternatively, by executing `./gradlew integrationTest`