# Project Boundary

This document defines the boundary between the METT evaluation website, the METT evaluation admin backend, and the METT HDR independent platform.

METT HDR is an independent SaaS platform. It must not reuse the admin-web Cookie Session from the METT evaluation backend. Server-to-server integration must use dedicated integration credentials, signatures, or integration tokens.

## System Relationship

```txt
mett-website:
  Public METT evaluation website frontend.

mett-admin:
  METT evaluation website admin backend and evaluation business management system.

mett-hdr:
  Independent healthy design analysis SaaS platform.
```

## Boundary Reminders

Evaluation ProjectMembership is not HDR membership.

Evaluation ApplicationAccess is not HDR login permission.

Evaluation product filing records are not the HDR product performance library.

Evaluation file permissions do not automatically grant HDR file download permission.

Evaluation admin Cookie Session is not an HDR integration credential.
