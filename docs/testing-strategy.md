# Testing Strategy and Test Plan

The test suite follows a deliberate pyramid: fast tests protect business rules first, while slower tests verify framework wiring, persistence, and the public HTTP contract. Every layer has a distinct purpose; the same behavior is not copied indiscriminately across all layers.

```mermaid
flowchart BT
    Unit[Unit tests\nOrderService business rules]
    Slice[MVC and JPA slice tests\nValidation, error mapping, Flyway, repository queries]
    Integration[Spring Boot HTTP integration tests\nOrder lifecycle and catalog CRUD]
    Acceptance[PostgreSQL Testcontainers acceptance test\nFlyway migration plus live HTTP]
    Unit --> Slice --> Integration --> Acceptance
```

| Layer | Scope | Examples | Runtime expectation |
| --- | --- | --- | --- |
| Unit | One class with mocked collaborators | Duplicate lines, insufficient inventory, snapshots, single cancellation | Fast and deterministic |
| MVC slice | Controller and validation/error boundary | Invalid JSON payloads and domain-exception mapping | No database |
| JPA slice | Repository and Flyway-backed schema | Seed data and deterministic lock-query ordering | H2 in PostgreSQL compatibility mode |
| HTTP integration | Application through `MockMvc` | Catalog CRUD, order placement, rollback, snapshots, cancel, delete guard | In-memory H2 test profile |
| Acceptance | Running server plus real PostgreSQL | Flyway seed data served through HTTP | Testcontainers / Docker |

## Primary scenarios

- Catalog create, list, retrieve, replace, delete, validation, and case-insensitive duplicate-name conflict.
- Order placement with inventory decrement and immutable item snapshots.
- Atomic rollback when a request includes an unknown coffee or exceeds available inventory.
- Duplicate coffee-line rejection before inventory is locked or an order is written.
- One-time cancellation, inventory restoration, and deletion allowed only after the order is cancelled.
- Generated OpenAPI and Swagger UI availability.

## Running tests

```bash
mvn verify
```

The default test profile is self-contained and uses H2 only as an isolated test database. The PostgreSQL acceptance test is configured to skip when Docker is unavailable, so local verification remains useful without a container runtime. In an environment with Docker, the same test runs against `postgres:16-alpine`; CI should provide Docker so that this test is executed rather than skipped.

## Test data and secrets

The H2 test profile uses `SA` with an empty password because the database is ephemeral and exists only during test execution. It is not a deployable account and no production credential is committed. Production database values are supplied by environment variables.
