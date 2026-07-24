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

## Identity And Authentication Foundation

This backend now includes HDR-local identity, password hashing with BCrypt, JWT access token issuing, HttpOnly refresh token lifecycle support, RBAC seed structures, external identity binding abstractions, and audit log foundations.

METT identity integration is intentionally boundary-only in this stage. The backend does not call `mett-admin`, share METT Admin sessions, read METT Admin user tables, or synchronize real METT users.

## Planned Scope

- User and authentication
- Team and organization
- Membership and quota
- Payment and orders
- Project and space
- File and report
- Product health performance database
- Product parameter templates
- Rules and matching
- Experts and tutorials
- Integration APIs
- Audit and jobs
- Lighting module MVP

## Non-Goals For This PR

This PR does not implement user registration, login, admin login, JWT or token business flow, membership plans, quota rules, payment orders, product database business, product matching, file upload, protected file download, report generation, PDF generation, lighting analysis, expert display, tutorial center, real evaluation backend API calls, SSO, account binding, mobile app, mini program, or open API commercialization.
