# Local Development

## Java Version

Use Java 21 LTS.

## MySQL Requirement

Use MySQL 8 for local database development.

Expected placeholders:

```env
HDR_DB_HOST=127.0.0.1
HDR_DB_PORT=3306
HDR_DB_NAME=mett_hdr
HDR_DB_USER=mett_hdr
HDR_DB_PASSWORD=change_me
```

## Redis Requirement

Use Redis for cache, session, and task support foundations.

Expected placeholders:

```env
HDR_REDIS_HOST=127.0.0.1
HDR_REDIS_PORT=6379
HDR_REDIS_PASSWORD=
```

## Environment Variables

All secrets must come from environment variables. Do not commit real database passwords, Redis passwords, integration secrets, payment keys, storage credentials, SMTP credentials, or private keys.

## Run Migrations

```bash
cd hdr-api
mvn flyway:info
```

Configure Flyway connection values through local Maven or environment settings before running against a real database.

## Start Local Server

```bash
cd hdr-api
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

## Run Tests

```bash
cd hdr-api
mvn test
```

## Open API Docs

After the local server starts, open:

```txt
http://localhost:8080/swagger-ui.html
```
