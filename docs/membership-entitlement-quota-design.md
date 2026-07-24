# Membership, Entitlement And Quota Design

## Purpose

PR 4 adds the reusable SaaS membership layer for METT HDR. It does not add
payments, pricing, checkout, renewals, projects, files, reports, or membership
frontends.

The membership layer is independent from:

- platform RBAC (`USER`, `ADMIN`);
- organization membership (`OWNER`, `ADMIN`, `MEMBER`);
- team membership (`LEAD`, `MEMBER`);
- resource ownership and contextual authorization.

An entitlement is one permission layer. It never grants access to another
user's or team's private resources by itself.

## Membership Subjects

A membership belongs to exactly one subject:

- `USER` for personal plans;
- `TEAM` for team plans.

Organizations do not receive SaaS memberships in this foundation. Database
constraints require a user membership to contain only `user_id` and a team
membership to contain only `team_id`.

The nullable `current_marker` supports one current membership per subject while
allowing any number of historical rows. Replaced, cancelled, and expired rows
retain their plan and usage history.

## Plans And Versioning

Plans are immutable versioned definitions identified by `(plan_code, version)`.
The foundation seeds:

- `PERSONAL_FREE` version 1;
- `PERSONAL_PLUS` version 1;
- `PERSONAL_PRO` version 1;
- `TEAM_PLUS` version 1;
- `TEAM_PRO` version 1.

Only `PERSONAL_FREE` is the default. No Team Free plan is created. Plan
activation validates the plan audience against the membership subject.

The seeded Plus and Pro rows establish identifiers and entitlement boundaries;
they do not represent pricing, purchase availability, or approved commercial
limits.

## Default Personal Free Provisioning

V4 backfills every existing HDR user that has no current personal membership.
New HDR-local registration provisions the default Personal Free membership in
the same transaction as:

1. user creation;
2. profile creation;
3. default platform-role assignment;
4. registration and membership audit records.

A provisioning failure rolls back the registration. Repeated provisioning
returns the existing current membership and does not create duplicates.

Teams are not provisioned automatically. A team may exist without a SaaS
membership until a future verified backend workflow activates Team Plus or
Team Pro.

## Entitlement Definitions

Entitlement definitions have stable codes and one of two types:

- `BOOLEAN` grants or denies a capability;
- `QUOTA` describes a measured capability.

Missing, disabled, or inactive definitions deny access. Suspended or inactive
memberships provide no normal entitlement access.

The migration seeds access assignments only:

- Personal Free, Plus, and Pro enable `HDR_ACCESS`;
- Team Plus and Pro enable `TEAM_WORKSPACE_ACCESS`.

Quota definition codes are reserved as capability categories, but plans receive
no production quota limits in this PR.

## Quota Entitlements

A quota entitlement is either:

- limited, with a non-negative `quota_limit`; or
- unlimited, with `is_unlimited=true` and no numeric hard limit.

At membership activation, the service snapshots each enabled quota definition
into `membership_usage`. Remaining quota is derived as:

```text
quota_limit_snapshot - used_count - reserved_count
```

Unlimited usage returns no numeric remaining limit. Usage and reservation
counts may never become negative, and limited totals may never exceed their
snapshot limit.

## Quota Transactions And Idempotency

Every mutation creates an immutable `quota_transactions` row with before and
after counters. The globally unique `operation_key` makes mutations idempotent:

- repeating the same operation returns the first result;
- reusing a key with conflicting operation data returns `409`;
- a replay never changes usage twice.

Quota mutation methods lock the current membership and usage row with
`SELECT ... FOR UPDATE`. Mutations are persisted in the database, not held in
memory.

Supported internal operations are:

- consume: increase used;
- restore: decrease used;
- reserve: increase reserved;
- commit reservation: move reserved to used;
- release reservation: decrease reserved;
- adjust: controlled internal correction with an audit record.

Reservation keys connect reserve, commit, and release ledger entries. Commit or
release cannot exceed the outstanding reservation.

There are no public quota mutation or adjustment endpoints.

## Usage Cycles And Snapshots

Plans currently use monthly cycles. Membership and entitlement queries lazily
detect expired periods; no scheduler, queue, Redis worker, or async-job table is
introduced.

Rollover:

1. locks the current membership;
2. creates one immutable JSON snapshot for the closing cycle;
3. preserves old `membership_usage` rows;
4. advances the membership period;
5. initializes the next cycle's quota rows;
6. records `USAGE_CYCLE_ROLLOVER`.

The membership lock and unique snapshot constraint prevent duplicate rollover.
Snapshots record the subject, plan code/version, entitlement code, quota
snapshot, used/reserved counts, cycle dates, and generation time.

Replacing a membership snapshots the current cycle when usage exists, marks the
old row `REPLACED`, clears its current marker, and creates the new current row.

## Read APIs

Authenticated personal queries:

```text
GET /api/v1/app/memberships/me
GET /api/v1/app/memberships/me/entitlements
GET /api/v1/app/memberships/me/usage
```

Authenticated team queries:

```text
GET /api/v1/app/teams/{globalTeamId}/membership
GET /api/v1/app/teams/{globalTeamId}/membership/entitlements
GET /api/v1/app/teams/{globalTeamId}/membership/usage
```

Team queries require active contextual team membership. Outsiders receive a
protected `404`; platform `ADMIN` has no automatic private-team access.
Responses expose global IDs only and never expose membership database IDs.

## Authorization Layering

HDR authorization retains four independent layers:

1. platform role permission;
2. membership entitlement;
3. resource ownership or contextual membership;
4. module availability.

PR 4 implements layer 2. A paid tier does not grant team management, private
resource access, or module permission without the other required layers.

## Audit Actions

The foundation supports:

```text
MEMBERSHIP_DEFAULT_PROVISION
MEMBERSHIP_PLAN_ACTIVATE
MEMBERSHIP_PLAN_REPLACE
MEMBERSHIP_SUSPEND
MEMBERSHIP_REACTIVATE
MEMBERSHIP_EXPIRE
USAGE_CYCLE_ROLLOVER
QUOTA_ADJUST
```

Quota mutation details live in the immutable quota ledger. Audit and quota rows
must not contain passwords, JWTs, refresh tokens, cookies, or payment
credentials.

## Future Payment Boundary

A later verified backend order/payment transition may call the internal
provisioning services. This PR does not:

- process or simulate payments;
- store prices, currency, invoices, orders, or provider fields;
- expose public upgrade or plan-activation APIs;
- implement renewal or subscription billing;
- implement membership purchase pages.

Commercial quota values and all user-facing membership UI remain deferred.
