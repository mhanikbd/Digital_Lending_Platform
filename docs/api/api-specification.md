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

## 8. Rules for new endpoints

Every endpoint must have: request validation, an authorisation rule, the standard
envelope, error handling through `GlobalExceptionHandler`, logging with the
correlation id, and OpenAPI annotations. Controllers accept and return DTOs;
JPA entities are never exposed.

State-changing calls that reach an external system accept an `Idempotency-Key`
header, so a retry after a timeout cannot double-post a disbursement or payment.
