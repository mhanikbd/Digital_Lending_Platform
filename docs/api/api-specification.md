# API specification

The machine-readable contract is published by the running backend:

| Artifact | URL |
| -------- | --- |
| OpenAPI document | `http://localhost:8080/v3/api-docs` |
| Swagger UI | `http://localhost:8080/swagger-ui.html` |

Both are disabled in the `prod` profile. This page documents the conventions the
generated document cannot express.

## 1. Versioning

Every endpoint lives under `/api/v1`. A breaking change introduces `/api/v2`
alongside the existing version; it never mutates `v1` in place.

## 2. Response envelope

Every `/api/**` response, success or failure, uses one shape:

```json
{
  "success": true,
  "data": { "...": "endpoint specific payload" },
  "correlationId": "0f2b9a44-9a1e-4a55-b6f1-6f1d4b3a2c77",
  "timestamp": "2026-08-18T11:04:21.115Z"
}
```

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "Request validation failed",
    "violations": [
      { "field": "amount", "message": "must be greater than 0" }
    ]
  },
  "correlationId": "0f2b9a44-9a1e-4a55-b6f1-6f1d4b3a2c77",
  "timestamp": "2026-08-18T11:04:21.115Z"
}
```

Null properties are omitted, so `data` is absent on failure and `error` is absent
on success. Clients should treat both as optional.

## 3. Decimal values are strings

Money, rates and factors are serialised as JSON **strings** in plain notation:

```json
{ "principal": "50000.0000", "interestRate": "9.000000", "emi": "4387.5000" }
```

A JSON number would be parsed by a JavaScript client into a 64-bit float and
silently lose precision. Parse these with a decimal library; never with
`parseFloat` or `double.parse`.

## 4. Error codes

| Code | HTTP | Meaning |
| ---- | ---- | ------- |
| `VALIDATION_FAILED` | 400 | One or more fields failed validation; see `violations` |
| `MALFORMED_REQUEST` | 400 | The body could not be parsed |
| `UNAUTHENTICATED` | 401 | No valid credential was presented |
| `ACCESS_DENIED` | 403 | Authenticated, but not permitted |
| `RESOURCE_NOT_FOUND` | 404 | No such resource, or it is outside the caller scope |
| `METHOD_NOT_ALLOWED` | 405 | Wrong HTTP method for this path |
| `CONFLICT` | 409 | Conflicts with current state |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Content type not supported |
| `BUSINESS_RULE_VIOLATION` | 422 | Well-formed, but breaks a business rule |
| `DEPENDENCY_UNAVAILABLE` | 503 | A downstream dependency is unavailable |
| `INTERNAL_ERROR` | 500 | Unexpected failure; detail is logged, not returned |

Codes are part of the contract. Branch on `error.code`, never on `error.message`.

A 404 is returned rather than a 403 when a resource exists but lies outside the
caller organisational scope, so the API does not confirm the existence of records
a user may not see.

## 5. Correlation ids

Send `X-Correlation-Id` on every request. It is returned as a response header and
in the envelope, and it appears in every server log line for that request.

A supplied value is echoed only when it matches `[A-Za-z0-9._-]{8,64}`; anything
else is replaced with a generated id. Client input reaches the log file, so a
value containing newlines could otherwise forge log entries.

## 6. Authorisation

The API is closed by default: anything that is not explicitly permitted requires
authentication. Public paths today are the platform endpoints and the OpenAPI
document.

Authentication itself arrives in Milestone 5 (customer PIN and OTP, bank user
credentials and MFA, field officer device binding) and database-driven RBAC in
Milestone 6. Until then no protected endpoint exists to call.

Workflow permissions will never be inferred client-side. The UI asks the backend
what the current user may do:

```
GET /api/v1/loan-applications/{id}/available-actions
```

## 7. Endpoints available today

### `GET /api/v1/platform/health`

Infrastructure connectivity. Always answers 200, including when a dependency is
down, so an operator can see which one. This is a diagnostic view, not a probe:
liveness and readiness use `/actuator/health` on the internal management port.

```json
{
  "success": true,
  "data": {
    "status": "UP",
    "components": [
      { "name": "database", "status": "UP", "detail": "reachable" },
      { "name": "cache", "status": "UP", "detail": "reachable" },
      { "name": "objectStorage", "status": "UP", "detail": "reachable" }
    ]
  },
  "correlationId": "…",
  "timestamp": "…"
}
```

Detail is deliberately coarse. The endpoint is unauthenticated, so it must not
disclose hostnames, driver versions or stack traces.

### `GET /api/v1/platform/info`

Application name, API version, active environment and authoritative server time.


### Authentication

All sign-in endpoints are unauthenticated by necessity: they are how a caller
obtains a token. Every one of them writes a row to `auth.t_login_history`,
successful or not.

| Method | Path | Purpose |
| ------ | ---- | ------- |
| POST | `/api/v1/auth/bank/login` | Employee id and password. Answers `AUTHENTICATED` with tokens, or `MFA_REQUIRED` with a challenge id |
| POST | `/api/v1/auth/bank/mfa` | Presents the code for a challenge raised above |
| POST | `/api/v1/auth/customer/otp` | Raises an OTP challenge for a mobile number |
| POST | `/api/v1/auth/customer/otp/verify` | Verifies the code and promotes the handset to `TRUSTED` |
| POST | `/api/v1/auth/customer/login` | Mobile number and 6 digit PIN, from a bound device |
| POST | `/api/v1/auth/token/refresh` | Exchanges a refresh token for a new pair, rotating it |
| POST | `/api/v1/auth/logout` | Revokes the session. Idempotent |
| GET | `/api/v1/auth/me` | The identity behind the bearer token. **Requires a token** |

Two properties of these endpoints are deliberate and should not be softened.

**Every failure answers identically.** Wrong password, unknown username,
suspended account and untrusted device all return `401` with
`UNAUTHENTICATED` and the message *Credentials were not accepted*. Which one
actually applied is in the audit trail. The endpoint cannot be used to discover
who banks here.

**Access tokens are short and cannot be revoked.** They are signed JWTs with a
15 minute life, carrying subject, username, user type and display name - and
deliberately no roles or scopes, which arrive with Milestone 6. Revocation acts
on the refresh token, which is opaque and server-side.


### Demonstration accounts - local profile only

| Method | Path | Authentication |
| ------ | ---- | -------------- |
| GET | `/api/v1/auth/demo-accounts` | None |

Returns the six seeded staff accounts **with their passwords**, so the sign-in
page can offer them as one-click cards. Registered only under the backend's
`local` profile; in any other environment the bean does not exist, the path is
not permitted, and the request answers 404.

See [security](../security/security.md) for the three guards that make this
acceptable, and [development setup](../deployment/development-setup.md) for the
roster.

### Access administration

Guarded by permission, not by role. A bank that decides its Unit Heads may
read the role catalogue inserts one row in `auth.t_role_permission`; nothing
here changes.

| Method | Path | Permission required |
| ------ | ---- | ------------------- |
| GET | `/api/v1/admin/roles` | `admin.role.view` |
| GET | `/api/v1/admin/permissions` | `admin.role.view` |
| GET | `/api/v1/admin/users` | `admin.user.view` |

A caller holding no matching permission gets `403` with `ACCESS_DENIED`; a
caller with no token at all gets `401` with `UNAUTHENTICATED`. The distinction
is deliberate: the first has proved who they are and been refused, the second
has not proved anything.

Reads only. Creating users and re-permissioning roles arrive with the
administration screens, together with the audit trail those changes require.

### Authority in the token

An access token carries `roles` and `perms` claims holding the codes its
holder had at issue. `/api/v1/auth/me` returns the same two lists, so a client
can hide a control it would only be refused for pressing. Both are advisory:
the server refuses the call regardless of what the client chose to render.

### Organization

| Method | Path | Permission required |
| ------ | ---- | ------------------- |
| GET | `/api/v1/organization/unit-types` | `organization.view` |
| GET | `/api/v1/organization/units` | `organization.view` |
| GET | `/api/v1/organization/my-scope` | none beyond being signed in |

The hierarchy is returned as a tree, with each unit carrying its children,
because a caller who asks for an organisation almost always wants to draw one.

`my-scope` returns the widest scope the caller's roles grant, the units they
are posted to, and every unit those two facts together make visible. Both
inputs are returned alongside the answer on purpose: "you can see three
branches" is unactionable, while "your role is branch-scoped and you hold three
postings" tells an administrator which of the two to change.

Reads only. Building the hierarchy is an administration screen, and the audit
trail that moving a branch between regions requires does not exist yet.

### Customers

| Method | Path | Permission required |
| ------ | ---- | ------------------- |
| GET | `/api/v1/customers` | `customer.view` |
| GET | `/api/v1/customers/{customerId}` | `customer.view` |

Two gates, not one. The permission decides whether a caller may read customers
at all; their organisational scope decides which ones. Holding `customer.view`
at a branch does not open the bank's whole book.

A customer outside the caller's scope answers **404, not 403** - exactly as one
that does not exist. A 403 would confirm the customer id is real and merely
held elsewhere, which turns the endpoint into a way of locating people.

The detail response carries personal data: parentage, date of birth, income and
document numbers. Monetary fields are JSON strings, per section 3.

Reads only. Creating and amending customers is the account-opening journey,
which needs KYC verification to mean anything first.

### Products

| Method | Path | Permission required |
| ------ | ---- | ------------------- |
| GET | `/api/v1/products` | `product.view` |
| GET | `/api/v1/products/{code}` | `product.view` |
| POST | `/api/v1/products` | `product.configure` |
| POST | `/api/v1/products/{code}/versions` | `product.configure` |
| PUT | `/api/v1/products/{code}/versions/{versionNo}` | `product.configure` |
| POST | `/api/v1/products/{code}/versions/{versionNo}/activate` | `product.configure` |
| POST | `/api/v1/products/{code}/versions/{versionNo}/retire` | `product.configure` |

The listing carries the version currently on sale, in full - terms, fees and
per-grade ceilings - because a catalogue that shows a rate but not the processing
fee shows a customer less than half of what a loan costs. Only the live version
is loaded, never the history, so a product repriced fifty times costs the same
two selects as one repriced once.

There is no endpoint that edits a live version. Repricing drafts a copy, the copy
is amended, and activating it retires the incumbent in the same transaction.
Amending anything that is not a draft answers **409 CONFLICT** naming the status
that refused it. So does starting a second draft while one is open.

See [product configuration](../product/product-configuration.md).

### Eligibility

| Method | Path | Permission required |
| ------ | ---- | ------------------- |
| POST | `/api/v1/eligibility/check` | `eligibility.check` |

```json
{ "customerId": "CIF-000001", "productCode": "ELOAN" }
```

No amount, no rate, no tenure in the request: the endpoint answers what the
customer qualifies for, not whether a figure somebody already chose is
acceptable.

Two gates again. The permission decides who may assess; the caller's
organisational scope decides whose customers. A customer outside it answers
**404**, exactly as on the customer endpoints and for the same reason.

The response carries the decision, the amount, every criterion with its result,
every limit that was considered - bound or not - and the id of the audit record
the run was written to. A declined customer carries no amount: quoting a limit to
somebody who has just been declined produces a figure that reads like an offer.

See [eligibility and loan amount engines](../product/eligibility-engine.md).

### Rules

| Method | Path | Permission required |
| ------ | ---- | ------------------- |
| GET | `/api/v1/rules/groups` | `rules.view` |
| GET | `/api/v1/rules/attributes` | `rules.view` |

Read only until Milestone 21 brings maker and checker. Each rule is returned
spelt out in the same words that appear in a decline, so a banker explaining one
and the customer receiving it are looking at the same sentence.

### Loan calculator

| Method | Path | Permission required |
| ------ | ---- | ------------------- |
| POST | `/api/v1/loan-calculator` | `product.view` |

```json
{ "productCode": "ELOAN", "amount": "35000", "tenureMonths": 12 }
```

The rate is not in the request - it comes from the live product version. A client
may show an indicative figure, but the authoritative answer is this one, and it
could not be if the client chose the inputs to it.

`rateOverride` additionally requires `product.price`. Supplying it without that
permission is **refused**, not ignored: silently quoting a different rate from
the one asked for is worse than saying no. A negotiated quote carries
`rateNegotiated: true`.

An amount or tenure the product does not offer answers **422** naming the bounds
that were applied.

See [pricing and the loan calculator](../product/pricing-and-calculator.md).

## 8. Rules for new endpoints

Every endpoint must have: request validation, an authorisation rule, the standard
envelope, error handling through `GlobalExceptionHandler`, logging with the
correlation id, and OpenAPI annotations. Controllers accept and return DTOs;
JPA entities are never exposed.

State-changing calls that reach an external system accept an `Idempotency-Key`
header, so a retry after a timeout cannot double-post a disbursement or payment.
