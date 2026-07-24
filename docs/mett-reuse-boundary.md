# METT Reuse Boundary

METT HDR is an independent SaaS platform inside the METT digital ecosystem. It can reuse design experience and integration patterns, but must not couple itself to METT Admin runtime state or data ownership.

## Reusable

- Authentication design experience
- RBAC model experience
- Audit log design
- File metadata-first thinking
- Integration design

## Not Reused

- METT Admin accounts
- Admin Session
- Admin backend permissions
- METT Admin database tables

## Boundary Notes

HDR users are HDR-owned identities. METT Website or METT Admin identities can be bound through external identity links and future integration APIs, but not by sharing cookies, sessions, database rows, or permission tables.
