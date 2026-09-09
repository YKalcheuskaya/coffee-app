# Coffee App

Transactional Coffee Ordering API built with Java 17 and Spring Boot.

## What it demonstrates

- Java 17, Spring Boot, Spring Data JPA, Bean Validation, Flyway, PostgreSQL, and H2 for isolated tests.
- Full CRUD for the live coffee catalog.
- Atomic order placement, deterministic pessimistic inventory locking, immutable item snapshots, and one-time cancellation restocking.
- Generated OpenAPI/Swagger, a layered test pyramid, Docker Compose, and Java 17 CI verification.

## Core workflow

1. Create or update a coffee catalog item.
2. Place an order for one or more distinct items.
3. Retrieve the immutable order snapshots.
4. Cancel the order to restore inventory exactly once, then delete the no-longer-active catalog item if needed.

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

## Run with Docker Compose

Copy `.env.example` to a local, Git-ignored `.env` file and replace both password placeholders. Then run:

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`; PostgreSQL data is retained in the local `postgres-data` Docker volume. Stop the stack with `docker compose down`.

## Project documentation

- [Architecture](docs/architecture.md)
- [Testing strategy and test plan](docs/testing-strategy.md)
- [Product and technical roadmap](docs/roadmap.md)
- Generated API contract at `/v3/api-docs` and Swagger UI at `/swagger-ui/index.html` while the app is running
