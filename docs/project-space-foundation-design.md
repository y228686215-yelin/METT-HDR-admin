# Project and Space Foundation

## Purpose

PR 6 establishes the project context used by future HDR capabilities. It
provides project ownership, contextual project members, project lifecycle,
hierarchical spaces, and a provider-neutral external object link boundary.

This foundation does not implement lighting, products, files, reports,
CAD/BIM parsing, external synchronization, or a real METT integration.

## Data Model

### Projects

`projects` stores an HDR-generated global project ID and project number,
business metadata, lifecycle status, and the reusable PR 3 ownership fields.
Application-created projects always use `origin_system = HDR`.

Project business fields include name, type, description, city, timezone,
activation/archive timestamps, immutable creator/owner identities, and the
transferable manager identity. Types are `RESIDENTIAL`, `SMALL_COMMERCIAL`,
and `OTHER`; statuses are `DRAFT`, `ACTIVE`, and `ARCHIVED`.

Ownership is one of:

- `PERSONAL`: no team or organization foreign key.
- `TEAM`: a team is required; an owning organization is derived from the team.
- `ORGANIZATION`: an organization is required and no team is present.

The creator and owner are immutable in this version. Project management is
transferred through the explicit manager-transfer operation.

### Project Members

`project_members` records contextual `MANAGER`, `EDITOR`, and `VIEWER` roles.
These roles are not platform RBAC roles. Membership history is preserved with
`ACTIVE`, `SUSPENDED`, and `LEFT` states.

Project creation writes the creator's active `MANAGER` membership in the same
transaction. Manager transfer locks the project and both memberships, changes
the previous manager to `EDITOR`, changes the target to `MANAGER`, updates
`managed_by_user_id`, and verifies that exactly one active manager remains.

### Project Spaces

`project_spaces` forms an adjacency-list hierarchy within one project. A space
is a `FLOOR`, `ROOM`, or `ZONE` with `RECTANGLE` or `UNSPECIFIED` geometry.
Parent assignment checks project ownership, parent status, self-parenting, and
cycles.

Rectangle area and volume are server-derived:

```text
floor_area_m2 = length_m * width_m
volume_m3 = floor_area_m2 * height_m
```

Derived values are stored at scale 3. Clients cannot submit area or volume.
Archiving is historical: records are not deleted, active children block parent
archive, and an archived child can only be restored after its parent.

### External Object Links

`external_object_links` is a generic mapping foundation. PR 6 supports internal
project-link service operations only:

```text
METT evaluation project <-> HDR project
```

There is no public application endpoint, external call, automatic import,
member synchronization, or use of an HDR user JWT for system integration.
Future integration APIs must authenticate systems independently and validate
external data before creating a link.

Future files, reports, rules, products, and METTLux capabilities may reference
stable project and space global IDs. They are deliberately absent from this
PR. Project-count and space-count quota enforcement is deferred because PR 4
defines no production entitlement values for those quotas; no arbitrary
commercial limit is invented here.

CAD, BIM, IFC, DWG, DXF, polygon geometry, coordinate systems, visual editors,
and all frontend work remain deferred.

## Authorization

All project APIs require a valid HDR access token and an active personal
membership with `HDR_ACCESS`.

| Operation | Required contextual authority |
| --- | --- |
| Read | Active project member, resource owner/manager, or authorized ownership-context manager |
| Edit project/space | Active project manager/editor, resource manager, or authorized ownership-context manager |
| Manage members | Current project manager, resource manager, or authorized ownership-context manager |
| Transfer manager | Current project manager, resource owner, or authorized ownership-context manager |

Team project creation and mutation additionally require active team membership
and the team's `TEAM_WORKSPACE_ACCESS` entitlement. Organization project
creation and mutation require active organization membership. Ordinary team or
organization membership alone does not reveal a project. Platform `ADMIN`
provides no automatic project access.

Ownership authorization and entitlement authorization are separate checks:
membership plan access never grants project visibility by itself, and project
membership never substitutes for a required team plan.

Targets added to team or organization projects must be active members of the
owning context. Personal projects may add any active HDR user.

## Lifecycle

Valid project transitions are:

```text
DRAFT -> ACTIVE
DRAFT -> ARCHIVED
ACTIVE -> ARCHIVED
ARCHIVED -> ACTIVE
```

Archived projects remain readable. Normal project, member, and space mutations
are blocked; restoration and necessary manager recovery remain explicit
operations. Space creation and mutation require an active project.

## APIs

Project operations:

```text
POST   /api/v1/app/projects
GET    /api/v1/app/projects
GET    /api/v1/app/projects/{globalProjectId}
PATCH  /api/v1/app/projects/{globalProjectId}
POST   /api/v1/app/projects/{globalProjectId}/activate
POST   /api/v1/app/projects/{globalProjectId}/archive
POST   /api/v1/app/projects/{globalProjectId}/restore
POST   /api/v1/app/projects/{globalProjectId}/manager-transfer
```

Member operations:

```text
GET    /api/v1/app/projects/{globalProjectId}/members
POST   /api/v1/app/projects/{globalProjectId}/members
PATCH  /api/v1/app/projects/{globalProjectId}/members/{globalUserId}
DELETE /api/v1/app/projects/{globalProjectId}/members/{globalUserId}
```

Space operations:

```text
GET   /api/v1/app/projects/{globalProjectId}/spaces
POST  /api/v1/app/projects/{globalProjectId}/spaces
GET   /api/v1/app/projects/{globalProjectId}/spaces/{globalSpaceId}
PATCH /api/v1/app/projects/{globalProjectId}/spaces/{globalSpaceId}
POST  /api/v1/app/projects/{globalProjectId}/spaces/{globalSpaceId}/archive
POST  /api/v1/app/projects/{globalProjectId}/spaces/{globalSpaceId}/restore
```

Responses expose global IDs rather than numeric database IDs or raw foreign
keys. Private inaccessible project and space lookups use protected `404`
responses where appropriate.

## Audit Actions

The foundation writes:

```text
PROJECT_CREATE
PROJECT_UPDATE
PROJECT_ACTIVATE
PROJECT_ARCHIVE
PROJECT_RESTORE
PROJECT_MEMBER_ADD
PROJECT_MEMBER_UPDATE
PROJECT_MEMBER_LEAVE
PROJECT_MANAGER_TRANSFER
PROJECT_SPACE_CREATE
PROJECT_SPACE_UPDATE
PROJECT_SPACE_ARCHIVE
PROJECT_SPACE_RESTORE
EXTERNAL_OBJECT_LINK_CREATE
EXTERNAL_OBJECT_LINK_DEACTIVATE
```

Audit records contain actor, action, resource type, global resource ID, source
system, and timestamp. Tokens, credentials, payment data, and external payloads
are not stored.
