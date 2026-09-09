# Product and Technical Roadmap

This repository is a finished portfolio project with a deliberately bounded local scope. The following items describe plausible next investment steps, not capabilities that are already delivered.

```mermaid
timeline
    title Coffee App evolution
    Current : Catalog CRUD
            : Transactional orders and cancellation
            : Flyway, OpenAPI, layered tests
    Next : Authentication and role-based authorization
         : Audit events and observability
    Later : Payment integration and idempotency keys
          : Inventory reservations and fulfillment workflow
    Scale : Asynchronous events
          : Reporting read models and operational dashboards
```

## Functional direction

- Add customer identities, staff roles, and authorization around catalog administration.
- Support idempotent order submissions and a payment authorization workflow.
- Introduce an inventory reservation or fulfillment state machine when order preparation becomes asynchronous.
- Add search, filtering, and pagination as the catalog grows.

## Architecture direction

- Publish domain events after committed order state changes to keep future notifications and analytics decoupled from the order transaction.
- Add an outbox pattern before integrating external services so business state and event delivery are reliable.
- Separate read-oriented reporting models only when reporting needs justify the additional complexity.

## Operational direction

- Add authenticated production profiles, secret management, health checks, structured logs, metrics, and trace correlation.
- Run database migrations and the PostgreSQL acceptance suite in continuous integration.
- Define database backup, retention, and recovery expectations before treating the service as deployable production software.
