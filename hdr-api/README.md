# hdr-api

`hdr-api` is the Java backend REST API foundation for METT HDR.

## Technical Direction

- Java 21
- Spring Boot
- Maven
- MySQL 8
- Redis
- Flyway
- REST API
- OpenAPI / Swagger
- JUnit-based tests
- Modular monolith first

## Build

```bash
mvn clean package
```

## Run Locally

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Local configuration uses environment variable placeholders only. Do not commit real credentials.

## Test

```bash
mvn test
```

## API Docs

After starting the service locally, open:

```txt
http://localhost:8080/swagger-ui.html
```

## Foundation APIs

- `GET /api/v1/health`
- `GET /api/v1/health/liveness`
- `GET /api/v1/health/readiness`
- `GET /api/v1/app/meta`
- `GET /api/v1/admin/meta`
- `GET /api/v1/integrations/evaluation/meta`
- `POST /api/v1/app/auth/register`
- `POST /api/v1/app/auth/login`
- `POST /api/v1/app/auth/refresh`
- `POST /api/v1/app/auth/logout`
- `GET /api/v1/app/users/me`
- `POST|GET /api/v1/app/organizations`
- `GET|PATCH /api/v1/app/organizations/{globalOrganizationId}`
- `GET|POST /api/v1/app/organizations/{globalOrganizationId}/members`
- `PATCH|DELETE /api/v1/app/organizations/{globalOrganizationId}/members/{globalUserId}`
- `POST /api/v1/app/organizations/{globalOrganizationId}/ownership-transfer`
- `POST|GET /api/v1/app/teams`
- `GET|PATCH /api/v1/app/teams/{globalTeamId}`
- `GET|POST /api/v1/app/teams/{globalTeamId}/members`
- `PATCH|DELETE /api/v1/app/teams/{globalTeamId}/members/{globalUserId}`
- `POST /api/v1/app/teams/{globalTeamId}/manager-transfer`
- `GET /api/v1/app/memberships/me`
- `GET /api/v1/app/memberships/me/entitlements`
- `GET /api/v1/app/memberships/me/usage`
- `GET /api/v1/app/teams/{globalTeamId}/membership`
- `GET /api/v1/app/teams/{globalTeamId}/membership/entitlements`
- `GET /api/v1/app/teams/{globalTeamId}/membership/usage`
- `GET /api/v1/app/membership-offers`
- `POST|GET /api/v1/app/orders`
- `GET /api/v1/app/orders/{globalOrderId}`
- `POST /api/v1/app/orders/{globalOrderId}/payment-attempts`
- `POST /api/v1/app/orders/{globalOrderId}/cancel`
- `POST /api/v1/integrations/payments/{providerCode}/callbacks`
- `POST|GET /api/v1/app/projects`
- `GET|PATCH /api/v1/app/projects/{globalProjectId}`
- `POST /api/v1/app/projects/{globalProjectId}/activate`
- `POST /api/v1/app/projects/{globalProjectId}/archive`
- `POST /api/v1/app/projects/{globalProjectId}/restore`
- `POST /api/v1/app/projects/{globalProjectId}/manager-transfer`
- `GET|POST /api/v1/app/projects/{globalProjectId}/members`
- `PATCH|DELETE /api/v1/app/projects/{globalProjectId}/members/{globalUserId}`
- `GET|POST /api/v1/app/projects/{globalProjectId}/spaces`
- `GET|PATCH /api/v1/app/projects/{globalProjectId}/spaces/{globalSpaceId}`
- `POST /api/v1/app/projects/{globalProjectId}/spaces/{globalSpaceId}/archive`
- `POST /api/v1/app/projects/{globalProjectId}/spaces/{globalSpaceId}/restore`

## Identity And Authentication Foundation

This backend now includes HDR-local identity, password hashing with BCrypt, JWT access token issuing, HttpOnly refresh token lifecycle support, RBAC seed structures, external identity binding abstractions, and audit log foundations.

METT identity integration is intentionally boundary-only in this stage. The backend does not call `mett-admin`, share METT Admin sessions, read METT Admin user tables, or synchronize real METT users.

## Organization, Team And Ownership Foundation

Organizations, contextual organization memberships, standalone and organization-owned teams, team memberships, owner/manager transfer, historical membership states, and reusable personal/team/organization ownership policies are persisted through JDBC. Platform RBAC remains separate from organization and team membership roles.

## Membership, Entitlement And Quota Foundation

Versioned Personal Free/Plus/Pro and Team Plus/Pro plan definitions, default
Personal Free provisioning, boolean and quota entitlement resolution, database
quota usage, immutable idempotent quota transactions, reservations, membership
history, and lazy monthly usage-cycle snapshots are available as backend
foundations.

Only authenticated membership and entitlement read APIs are public. Plan
activation and quota mutation remain internal services. The migration does not
seed commercial quota limits.

This foundation does not add payment providers, prices, currencies, checkout,
orders, invoices, renewals, projects, products, files, reports, lighting,
frontends, or real METT integration.

## Payment And Order Foundation

Versioned membership offers, personal and team purchase orders, persisted
idempotency, provider-neutral payment attempts, independently authenticated
callbacks, event deduplication, explicit state transitions, review handling,
and exactly-once paid membership fulfillment are available as backend
foundations.

The backend resolves all price, currency, plan, version, and duration values
from an active offer. Flyway seeds no production price. The included
`LOCAL_TEST` provider is disabled by default, profile-restricted, and requires
a runtime-only signing secret.

This foundation does not add a real payment provider, merchant credentials,
production checkout, frontend payment pages, recurring billing, renewals,
upgrades, refunds, invoices, tax handling, or payment administration UI.

## Project And Space Foundation

Personal, team, and organization-owned projects now use the reusable ownership
policy and membership entitlements. Project roles remain contextual, manager
transfer preserves exactly one active manager, and archived records remain
readable without supporting normal mutation.

Hierarchical project spaces support server-derived rectangle area and volume,
cycle-safe parent movement, and historical archive/restore behavior. Generic
external project links are available only through an internal service; no
public integration endpoint or external synchronization is included.
