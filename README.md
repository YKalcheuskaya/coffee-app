# Coffee App

Transactional Coffee Ordering API built with Java 17 and Spring Boot.

## Current status

Coffee catalog CRUD is available: create, list, retrieve, replace, and delete catalog items. Orders can be placed, retrieved, and cancelled. Order placement locks inventory rows in a consistent order, writes immutable item snapshots, and rolls back the entire operation when a requested item is invalid or unavailable. Cancelling a placed order restores inventory once; coffee with an active order cannot be deleted. PostgreSQL schema creation and seed data are managed by Flyway; generated OpenAPI and Swagger UI describe the running endpoints. Docker delivery and the remaining verification layers will be added through separate verified commits.

## Prerequisites

- Java 17
- Maven 3.6.3 or later

## Run and verify

```bash
mvn verify
```

`mvn verify` runs against an isolated H2 test profile. To run the application against PostgreSQL, export `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` using values derived from `.env.example`, then run:

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080` by default. `.env.example` is a placeholder-only template; Spring Boot does not load `.env` files automatically. When the application is running, open Swagger UI at `http://localhost:8080/swagger-ui/index.html`.

Catalog write endpoints are intentionally unauthenticated in this local portfolio scope. Authentication, authorization, and audit trails are future hardening work, not current production claims.

## Planned documentation

- Architecture and data-flow diagrams
- Testing strategy and test pyramid
- Product roadmap
- Generated OpenAPI contract and Swagger UI
