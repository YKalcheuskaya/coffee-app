# Repository Guidelines

## Structure

- Use Java 17 and Maven.
- Keep production code under `src/main/java/com/ykalcheu/coffee/app` and tests under the mirrored `src/test/java` path.
- Organize implementation by feature: `catalog`, `order`, and `shared.error`; keep API, application, domain, and persistence responsibilities cohesive.
- Keep resources under `src/main/resources`; never commit build output, local environment files, or credentials.

## Development workflow

- Use `mvn test` while iterating and `mvn verify` before each commit.
- Keep commits small, descriptive, and limited to one verified milestone.
- Add tests at the appropriate level: unit, web/API slice, persistence/integration, or black-box HTTP.

## Style and documentation

- Use four-space Java indentation, descriptive names, and focused methods.
- Add English comments only for non-obvious business rules, transactions, concurrency, or persistence decisions.
- Keep OpenAPI metadata aligned with implementation. Use Mermaid for architecture diagrams.
