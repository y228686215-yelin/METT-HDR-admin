# Identity Authentication Design

## HDR Identity Model

METT HDR owns its independent SaaS identity model. The core user identity is stored as an HDR user with a stable `global_user_id`, local numeric database ID, identity source, contact fields, password hash, status, profile, roles, refresh tokens, and audit events.

HDR-local users use:

- `users`
- `user_profiles`
- `roles`
- `permissions`
- `user_roles`
- `role_permissions`
- `auth_refresh_tokens`
- `audit_logs`

Passwords must be stored with BCrypt hashes. Refresh tokens must be stored only as hashes.

## METT Identity Binding Concept

METT HDR is independent from `mett-website` and `mett-admin`, but remains compatible with the broader METT ecosystem.

External identity relationships are represented through `user_identity_links`:

- `global_user_id`: HDR user identity.
- `source_system`: External system such as `METT_WEBSITE`.
- `external_user_id`: External local user identifier.
- `external_global_user_id`: External global user identifier when available.
- `identity_type`: Identity provider category.

This allows future account binding and SSO without sharing the METT Admin database, cookies, sessions, or permission model.

## Token Lifecycle

HDR Web authentication uses:

- Access Token: JWT with `userId`, `globalUserId`, and `roles`.
- Refresh Token: HttpOnly cookie value stored server-side only as a hash.

Lifecycle:

1. Register creates an HDR user, profile, default `USER` role, and audit log.
2. Login verifies the HDR-local credential, returns a JWT access token, stores a hashed refresh token, and writes audit.
3. Refresh validates the refresh cookie, revokes the old refresh token, issues a new access token and refresh token, and writes audit.
4. Logout revokes the refresh token, clears the cookie, and writes audit.

## Future SSO Strategy

Future SSO can be added by implementing additional `IdentityProvider` variants:

- METT Identity Provider
- External Identity Provider
- Enterprise SSO Provider

These providers should authenticate through controlled integration APIs or trusted identity protocols. They must create or bind HDR users through `user_identity_links` instead of treating METT Admin users as HDR users.

## Security Boundary

Forbidden:

- Directly sharing the METT Admin database.
- Directly reading METT Admin user tables.
- Reusing METT Admin Cookie Session.
- Treating METT Admin users as rows in the HDR user table.
- Calling real METT login APIs in this PR.

Allowed:

- `global_user_id`
- `external_object_links`
- `user_identity_links`
- integration API based identity handoff in later PRs

Audit actions established in this PR:

- `REGISTER`
- `LOGIN_SUCCESS`
- `LOGIN_FAILED`
- `TOKEN_REFRESH`
- `LOGOUT`
- `IDENTITY_BIND`
