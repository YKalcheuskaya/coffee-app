# Coffee App

Transactional Coffee Ordering API built with Java 17 and Spring Boot.

## Current status

This repository contains the first implementation milestone: a runnable application foundation. Catalog CRUD, ordering, persistence, OpenAPI documentation, and layered verification will be added through small, verified commits.

## Prerequisites

- Java 17
- Maven 3.6.3 or later

## Run and verify

```bash
mvn spring-boot:run
mvn verify
```

The application starts on `http://localhost:8080` by default. Database configuration in `.env.example` is a placeholder-only template for a later persistence milestone; the initial application does not connect to a database.

## Planned documentation

- Architecture and data-flow diagrams
- Testing strategy and test pyramid
- Product roadmap
- Generated OpenAPI contract and Swagger UI
