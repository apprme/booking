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
configuration. Execute `docker-compose up` in the `docker` directory. NGINX will be available at `http://localhost:9000` 

### test.http

Refer to `test.http` for a simple demo of HTTP requests and responses. You should have a server running
on `localhost:8081` and have [HTTP Client](https://plugins.jetbrains.com/plugin/13121-http-client) plugin
installed in order to execute these requests from IDEA.