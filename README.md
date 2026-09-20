# Coffee App

Transactional Coffee Ordering API built with Java 17 and Spring Boot.

## What it demonstrates

- Java 17, Spring Boot, Spring Data JPA, Bean Validation, Flyway, PostgreSQL, and H2 for isolated tests.
- Full CRUD for the live coffee catalog.
- Atomic order placement, deterministic pessimistic inventory locking, immutable item snapshots, and one-time cancellation restocking.
- Idempotent order submission with a durable request fingerprint and conflict detection.
- A small browser client plus a Playwright end-to-end customer workflow.
- Generated OpenAPI/Swagger, a layered test pyramid, Docker Compose, and Java 17 CI verification.

## Core workflow

1. Create or update a coffee catalog item.
2. Place an order for one or more distinct items.
3. Retrieve the immutable order snapshots.
4. Cancel the order to restore inventory exactly once, then delete the no-longer-active catalog item if needed.

## Scope and behavior

Coffee catalog CRUD is available: create, list, retrieve, replace, and delete catalog items. Orders can be placed, retrieved, and cancelled. `POST /api/orders` requires an `Idempotency-Key` header. Repeating the same canonical item list with that key returns `200 OK`, `Idempotent-Replay: true`, and the original order without another inventory decrement. Reusing a key for a different list returns `409 IDEMPOTENCY_KEY_REUSED`. Until authentication exists, keys are global to this single application instance; a future identity feature must scope them to the authenticated customer.

Flyway manages PostgreSQL schema creation and seed data. Generated OpenAPI and Swagger UI describe the running endpoints. The repository includes Docker Compose, Java 17 CI verification, and a layered test suite that exercises both H2 and PostgreSQL through Testcontainers.

## Prerequisites

- Java 17
- Maven 3.6.3 or later
- Docker with its engine running (required for the complete `mvn verify` suite)
- Node.js 20 or later and npm (required only for the Playwright browser tests)

## Run and verify

```bash
mvn verify
```

`mvn test` runs the fast H2-backed suite. `mvn verify` runs the complete suite, including required PostgreSQL/Testcontainers acceptance tests, and writes a JaCoCo HTML report to `target/site/jacoco/index.html`.

### Docker/Testcontainers preflight

Before running `mvn verify`, make sure Docker is installed and its engine is running. Verify that the Docker client can reach the engine:

```bash
docker info
```

The output must include a `Server` section without a connection error. If it does not, start Docker Desktop (or the Docker service on your platform), wait until the engine is ready, and run the command again. Then rerun the complete suite:

```bash
mvn clean verify
```

If `PostgresCoffeeAppAcceptanceTest` reports `Could not find a valid Docker environment`, Testcontainers could not start its temporary PostgreSQL container. This is a local Docker configuration issue; the PostgreSQL acceptance test is intentionally required and should not be disabled.

To run the application directly against PostgreSQL, export `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` using values derived from `.env.example`, then run:

```bash
mvn spring-boot:run
```

The application starts on `http://localhost:8080` by default. `.env.example` is a placeholder-only template; Spring Boot does not load `.env` files automatically. When the application is running, open [Swagger UI](http://localhost:8080/swagger-ui/index.html) or the generated [OpenAPI document](http://localhost:8080/v3/api-docs).

The customer UI is served from [http://localhost:8080/](http://localhost:8080/). It deliberately covers the customer workflow only; catalog administration remains available through the documented API.

To explore the customer UI without configuring PostgreSQL, start the isolated local H2 profile:

```bash
mvn -Dspring-boot.run.profiles=ui spring-boot:run
```

Then open [http://localhost:8080/](http://localhost:8080/). The `ui` profile is local demonstration and browser-test infrastructure; it is not a production configuration.

## Browser automation

Install the JavaScript test dependency and Chromium once, then run the compact end-to-end regression:

```bash
npm ci
npx playwright install chromium
npm run test:ui
```

Playwright starts the Spring Boot application with its isolated H2 UI profile. It verifies the customer journey from catalog through cart, checkout, a manual order lookup after a page reload, and cancellation; it also verifies client-side quantity validation. Use Java 17 on `PATH` when running the command.

## Run with Docker Compose

Copy `.env.example` to a local, Git-ignored `.env` file and replace both password placeholders. Then run:

```bash
docker compose up --build
```

The API is available at `http://localhost:8080`; PostgreSQL data is retained in the local `postgres-data` Docker volume. Stop the stack with `docker compose down`.

PostgreSQL applies `POSTGRES_USERNAME` and `POSTGRES_PASSWORD` only when it initializes an empty volume. If a local `postgres-data` volume already exists, changing the password in `.env` will not change the database user's stored password. Keep the original local password to preserve that data, or, only when the local database is disposable, recreate it with:

```bash
docker compose down -v
docker compose up --build
```

The `-v` command deletes the local PostgreSQL data volume.

## Try the workflow

After starting the application, retrieve the Flyway-seeded catalog:

```bash
curl http://localhost:8080/api/coffees
```

Open [Swagger UI](http://localhost:8080/swagger-ui/index.html) to create or replace a catalog item, place an order, retrieve its immutable snapshots, cancel it, and then delete the now-inactive catalog item. Swagger UI is the canonical endpoint reference; this README intentionally does not duplicate it.

## Local-scope boundary

Catalog write endpoints are unauthenticated in this deliberately bounded portfolio project. Authentication, authorization, audit trails, operational monitoring, and deployment hardening are documented as future work and are not current production claims.

## Project documentation

- [Architecture](docs/architecture.md)
- [Testing strategy and test plan](docs/testing-strategy.md)
- [Product and technical roadmap](docs/roadmap.md)
- Generated API contract at `/v3/api-docs` and Swagger UI at `/swagger-ui/index.html` while the app is running
