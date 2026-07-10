# Sprint 1 Backend Foundation

## What This PR Does

- Initializes `hdr-api` as a Java 21 Spring Boot Maven backend project.
- Adds baseline Spring Web, Validation, Redis, Flyway, MySQL, OpenAPI, and test dependencies.
- Adds a unified API response envelope with `code`, `message`, `data`, `requestId`, and `timestamp`.
- Adds global exception handling without exposing stack traces to API clients.
- Adds request ID propagation through `X-Request-Id`, MDC logging, response headers, and response bodies.
- Adds route skeletons for App API, Admin API, and Evaluation Integration API.
- Adds health, liveness, and readiness endpoints.
- Adds a stable UUID-based `GlobalIdService` API.
- Adds Flyway baseline tables for system configuration and enum dictionaries.

## What This PR Does Not Do

- No user auth business.
- No admin auth business.
- No membership business.
- No quota business.
- No payment business.
- No product business.
- No file upload or protected download business.
- No report generation or PDF generation.
- No rules, matching, expert, tutorial, or lighting business.
- No real evaluation backend integration calls.
- No fake business records or seed data.

## Architecture Boundaries

METT HDR is an independent SaaS platform. App API, Admin API, and Integration API must use separate authentication models in later PRs.

The evaluation backend integration baseline is read-only and must use dedicated integration credentials, signatures, or tokens. It must not reuse the METT evaluation admin Cookie Session.

## API Groups

- App API: `/api/v1/app/**`
- Admin API: `/api/v1/admin/**`
- Evaluation Integration API: `/api/v1/integrations/evaluation/**`
- Foundation Health API: `/api/v1/health/**`

## Database Tables

- `system_configs`
- `enum_dictionaries`

No business tables are created in this PR.

## Global ID Rules

- External APIs should prefer global IDs.
- Local numeric database IDs are internal only.
- Global IDs are immutable after creation.
- Current implementation uses stable prefixes plus UUID values.

## Validation Commands

```bash
cd /Users/daniel/CodexMett-HDR/HDR-admin/hdr-api
mvn clean package
mvn test
mvn flyway:info
```
