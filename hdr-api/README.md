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

## Identity And Authentication Foundation

This backend now includes HDR-local identity, password hashing with BCrypt, JWT access token issuing, HttpOnly refresh token lifecycle support, RBAC seed structures, external identity binding abstractions, and audit log foundations.

METT identity integration is intentionally boundary-only in this stage. The backend does not call `mett-admin`, share METT Admin sessions, read METT Admin user tables, or synchronize real METT users.

## Organization, Team And Ownership Foundation

Organizations, contextual organization memberships, standalone and organization-owned teams, team memberships, owner/manager transfer, historical membership states, and reusable personal/team/organization ownership policies are persisted through JDBC. Platform RBAC remains separate from organization and team membership roles.

This foundation does not add subscription plans, quotas, payments, orders, projects, products, files, reports, lighting, frontends, or real METT integration.
