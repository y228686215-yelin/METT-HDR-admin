# Payment And Order Foundation Design

## Purpose

PR 5 adds the backend payment and order foundation for purchasing existing
Personal Plus, Personal Pro, Team Plus, and Team Pro memberships. It introduces
commercial offers, orders, payment attempts, verified provider events, state
history, and membership fulfillment without adding a production payment
provider or frontend checkout.

The backend is the commercial authority. Clients select an offer and subject;
they never provide an authoritative amount, currency, plan, duration, payment
status, or membership activation instruction.

## Offer Model

`membership_plan_offers` connects a versioned PR 4 membership plan to a
commercial offer. An offer records:

- a global offer ID and stable offer code;
- audience (`PERSONAL` or `TEAM`);
- currency and integer minor-unit amount;
- duration in months;
- publication status and optional availability window;
- an offer version used in the order snapshot.

Only active, currently available offers for active paid plan definitions are
returned by the application API. No production offer or price is seeded by
Flyway.

Amounts are stored as integer minor units. For example, a value of `1099`
represents 10.99 in a two-decimal currency. Floating-point money is not used.
The backend resolves amount and currency from the selected offer.

## Purchase Subjects And Authorization

An order has exactly one purchase subject:

- `USER`: the authenticated user purchasing a personal plan for themselves;
- `TEAM`: an authorized team manager purchasing a team plan.

Personal orders cannot target another user. Team purchase authority belongs to
an active team `LEAD`, or an active parent-organization `OWNER` or `ADMIN`.
Ordinary team members receive `403`. Outsiders and platform administrators
without contextual membership receive a protected `404`; platform RBAC does
not bypass private team authorization.

An existing active paid membership is not silently replaced. Personal purchase
is allowed only from Personal Free, and a team purchase requires that the team
have no current membership. Renewal, upgrade, downgrade, and replacement rules
need a later explicit commercial policy.

## Order Creation And Idempotency

The authenticated purchaser supplies an idempotency key when creating an
order. The database uniqueness boundary is the purchaser plus that key.
Repeating an equivalent request returns the original order. Reusing the key
with a different offer or subject returns `409`.

Order items preserve a server-generated purchase snapshot containing the
offer, plan code and version, amount, currency, and duration. Later offer
changes cannot alter an existing order.

Payment-attempt creation has a separate purchaser-scoped idempotency key.
Equivalent retries return the same attempt and cannot create duplicate
provider initiation records.

## Payment Provider Boundary

`PaymentProvider` separates provider-specific initiation and callback
verification from order and fulfillment services. PR 5 includes only
`LOCAL_TEST`, which:

- is disabled by default;
- may run only in a `local` or `test` profile;
- requires a runtime signing secret with no repository fallback;
- creates deterministic non-commercial provider references;
- verifies an HMAC-SHA256 signature over the exact callback body.

No real provider SDK, merchant credential, hosted checkout, or production
payment simulation endpoint is included.

The provider callback endpoint is public because it cannot depend on an HDR
user JWT. It is independently authenticated by the selected provider before
any event or business state is written. Invalid signatures return `401` and
leave orders, attempts, events, and memberships unchanged.

## Event Deduplication And Review

Every verified callback is identified by `(provider_code, provider_event_id)`.
Replays return the recorded result without repeating a state transition or
fulfillment. Provider transaction IDs are also checked to prevent one payment
transaction from satisfying multiple attempts.

The stored event contains a payload digest and sanitized verified fields, not
the raw callback or signing secret. A successful provider event must match the
order's exact amount and currency. Amount mismatch, currency mismatch, unknown
or conflicting provider references, and late success for a cancelled or
expired order move processing to `REVIEW_REQUIRED` rather than granting a
membership.

## Separate State Machines

Payment success and membership fulfillment are distinct facts:

- order state tracks purchase progression;
- payment state tracks provider payment progression;
- fulfillment state tracks membership provisioning.

The immutable transition ledger records entity type, previous state, new
state, reason, and actor. Services reject transitions not present in the
explicit state machine.

A verified matching success first commits the order and payment as `PAID`.
Fulfillment then runs in a separate transaction. Therefore a membership
failure does not erase evidence that money was accepted.

## Exactly-Once Membership Fulfillment

One fulfillment row and a stable operation key guard each order. Fulfillment
revalidates the subject and paid-membership policy, then invokes the internal
PR 4 provisioning service:

- a personal purchase replaces Personal Free with the purchased paid plan;
- an eligible team receives the purchased team plan;
- the membership source is `PAYMENT`;
- the purchased plan version and duration snapshot are preserved.

Success changes fulfillment to `FULFILLED` and the order to `FULFILLED`.
Repeated callbacks or retries return the existing result and do not create
duplicate memberships.

If provisioning fails, the payment and order remain `PAID`, fulfillment is
recorded as `FAILED`, and no partial membership change is committed. An
internal service can retry the same fulfillment operation exactly once. There
is no public endpoint for retrying fulfillment, marking an order paid, or
activating a membership.

## Frontend Security Boundary

A future success page must treat its redirect or query parameters only as a
display hint. It must query the authenticated backend for order status and
must never mark payment successful, activate membership, trust a provider
transaction ID supplied by the browser, or assume that a redirect proves
payment.

## Deferred Work

The following remain outside this foundation:

- recurring billing, automatic renewal, and renewal reminders;
- upgrade proration, downgrade scheduling, and active paid-plan replacement;
- refunds, disputes, chargebacks, invoices, and tax handling;
- production payment providers, merchant credentials, and production checkout;
- frontend checkout, payment success pages, and payment administration UI.
