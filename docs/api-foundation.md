# API Foundation

## Unified Response Envelope

All foundation APIs return:

```json
{
  "code": "SUCCESS",
  "message": "Success.",
  "data": {},
  "requestId": "req_xxx",
  "timestamp": "2026-07-05T00:00:00+08:00"
}
```

## Error Model

Supported response codes:

- `SUCCESS`
- `BAD_REQUEST`
- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `CONFLICT`
- `UNPROCESSABLE_ENTITY`
- `INTERNAL_SERVER_ERROR`

Validation errors return 400. Unauthorized placeholder errors return 401. Forbidden placeholder errors return 403. Not found errors return 404. Conflict errors return 409. Business state errors return 422. Unexpected errors return 500.

Stack traces are not returned to API clients.

## Request ID Behavior

- If `X-Request-Id` exists and is valid, it is reused.
- If it is missing or invalid, the backend generates `req_<uuid>`.
- The request ID is added to MDC logs.
- The request ID is returned in the `X-Request-Id` response header.
- The request ID is returned in the unified response body.
- Request context is cleared after request completion.

Request IDs are trace identifiers only. They are not security credentials.

## Health API

- `GET /api/v1/health`
- `GET /api/v1/health/liveness`
- `GET /api/v1/health/readiness`

Readiness reports database and Redis as `UNKNOWN` until real dependency checks are wired.

## Meta API

- `GET /api/v1/app/meta`
- `GET /api/v1/admin/meta`
- `GET /api/v1/integrations/evaluation/meta`

These endpoints expose route skeleton metadata only. They do not implement authentication or real integration behavior.
