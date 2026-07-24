# Organization, Team and Ownership Design

## Purpose

This foundation introduces contextual organization and team membership for the
independent METT HDR SaaS platform. It does not introduce subscription
membership, billing, projects, products, files, reports, or professional
modules.

## Organization Model

An organization has an immutable `global_organization_id`, an optional future
`global_company_id`, a type, lifecycle status, an original owner, a current
manager, and an immutable creator.

Organization membership roles are:

- `OWNER`: the single active owner and highest contextual authority.
- `ADMIN`: may manage ordinary members and organization data.
- `MEMBER`: may access the private organization but cannot administer it.

Membership lifecycle states are `ACTIVE`, `SUSPENDED`, and `LEFT`. Leaving is a
state transition; membership history is never physically deleted.

Organization creation, owner membership creation, and audit logging occur in
one transaction. Ownership transfer updates both owner memberships in one SQL
statement, changes `organizations.owner_user_id`, conditionally transfers
management, and verifies that exactly one active owner remains.

## Team Model

A team may be standalone or owned by one organization. The organization
reference is immutable in this version. Team roles are contextual:

- `LEAD`: manages team data and ordinary membership.
- `MEMBER`: participates in the team.

The creator becomes the original owner, current manager, immutable creator, and
an active `LEAD` in one transaction. For an organization team, the creator must
be an active organization `OWNER` or `ADMIN`, and every active team member must
also be an active parent-organization member.

Team management transfer requires the current manager or the owning
organization's `OWNER`. The target must already be an active team `LEAD`.
`created_by_user_id` and `owner_user_id` are not rewritten.

## Platform RBAC Boundary

Platform roles from the identity foundation (`USER`, `ADMIN`) remain in
`user_roles`. Organization roles (`OWNER`, `ADMIN`, `MEMBER`) and team roles
(`LEAD`, `MEMBER`) live only in their membership tables.

An organization `ADMIN` is not a platform `ADMIN`. A platform `ADMIN` receives
no automatic private-resource read, download, organization, or team access.

## Member Departure

Removing a member marks the membership `LEFT`; suspension marks it
`SUSPENDED`. Neither operation deletes organizations, teams, ownership
metadata, or future resources.

An organization owner, organization manager, or manager of an
organization-owned team must transfer the relevant responsibility before
leaving or suspension. When another organization member becomes inactive,
their active memberships in that organization's teams transition to the
corresponding inactive state.

A team manager must transfer management before leaving, suspension, or
demotion from `LEAD`.

## Reusable Resource Ownership

`ResourceOwnership` is independent of any business-resource table and records:

- `ownerUserId`: original or personal owner.
- `teamId`: team ownership context when present.
- `organizationId`: organization context, derived from the team when needed.
- `createdByUserId`: immutable creator.
- `managedByUserId`: transferable operational manager.

Supported scopes:

- `PERSONAL`: owner, creator, and manager are the authenticated user.
- `ORGANIZATION`: creator must be active in the organization. Ordinary members
  can appoint only themselves; `OWNER` and `ADMIN` may appoint another active
  member.
- `TEAM`: creator must be an active team member. The organization is derived
  from the team. Supplied team and organization IDs must match. A non-lead may
  appoint only themselves.

The policy service exposes assignment validation plus `canRead`, `canManage`,
and `canTransferManagement`. Team managers, active team leads, and owning
organization owners may manage team-scoped resources. Organization managers,
owners, and administrators may manage organization-scoped resources.

Later projects, products, reports, files, and professional modules may embed
these ownership fields and call this policy. This PR creates none of those
modules or tables.

## API And Global IDs

Public application APIs expose only `globalOrganizationId`, `globalTeamId`, and
`globalUserId`. Numeric IDs remain JDBC and foreign-key implementation details.
Private organization and team lookups return `404` to non-members.
Authentication is required for every endpoint, and authorization is enforced
inside services.

## METT Interoperability Boundary

`global_company_id` is reserved for a future cross-system company identity.
This version performs no METT account synchronization, `mett-admin` call,
session sharing, company mapping, or integration write-back.

## Explicitly Deferred

Subscription plans, quotas, billing, invitations, public directories,
certification, projects, spaces, products, files, reports, jobs, lighting,
frontend work, and real METT integration remain out of scope.
