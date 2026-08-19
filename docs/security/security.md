# Security

## 1. Current posture

Milestone 1 establishes the shape of the security model. Authentication itself is
Milestone 5. The important property today is that the API is **closed by
default**, so nothing becomes reachable by accident as endpoints are added.

| Control | State |
| ------- | ----- |
| Stateless API, no sessions or cookies | In place |
| `anyRequest().authenticated()` | In place |
| Standard error envelope for 401 and 403 | In place |
| Security headers | In place |
| CORS restricted to configured origins | In place |
| Actuator on an unpublished management port | In place |
| Secrets read from the environment | In place |
| Correlation id sanitised before logging | In place |
| Internal failure detail withheld from clients | In place |
| Non-root container users | In place |
| Authentication, MFA, JWT, RBAC | Milestone 5-6 |
| Rate limiting and brute-force lockout | Milestone 5 |
| Field-level encryption for NID and TIN | Milestone 8 |
| Upload content validation and virus scanning | Milestone 9 |
| Full audit trail | Milestone 40 |

## 2. Filter chains

Two chains, in `security/SecurityConfig.java`:

1. **Actuator chain** — matches actuator endpoints and permits them. It is safe
   because actuator is bound to port 9091, which compose does not publish and
   Nginx does not proxy. Prometheus scrapes it inside the container network.
2. **API chain** — CSRF disabled (no cookies, so there is nothing to forge
   against), CORS from configuration, stateless sessions, HTTP Basic and form
   login disabled, security headers, and `anyRequest().authenticated()` after a
   short list of public paths.

Public paths are `/api/v1/platform/health`, `/api/v1/platform/info`, and the
OpenAPI document and UI. The OpenAPI endpoints are disabled entirely in the
`prod` profile.

## 3. Headers

| Header | Value | Purpose |
| ------ | ----- | ------- |
| `X-Content-Type-Options` | `nosniff` | Prevents MIME sniffing |
| `X-Frame-Options` | `DENY` | Prevents framing of API responses |
| `Referrer-Policy` | `no-referrer` | Keeps URLs out of third-party referrers |
| `Strict-Transport-Security` | 1 year, subdomains | Enforces HTTPS once TLS terminates |
| `Cache-Control` | `no-store` on authenticated responses | Keeps customer data out of caches |

The portal sets the same content-type, frame and referrer headers, plus a
`Permissions-Policy` denying camera, microphone and geolocation.

## 4. Secrets

No credential is committed. `application.yml` reads every secret from an
environment variable; the development defaults it falls back to are local-only
values, and the `prod` profile has no defaults at all so a missing variable fails
startup rather than silently using a weak value.

`.env` is gitignored; `.env.example` carries placeholders. In a deployed
environment the variables come from the platform secret store.

Never logged, in any environment: PIN, password, OTP, tokens, object storage
credentials, full NID or account numbers.

## 5. Input handling

- Bean Validation on every request DTO; failures return `VALIDATION_FAILED` with
  per-field detail.
- Rejected values are **not** echoed back in violations, because request bodies
  in this platform routinely carry PII.
- The correlation id is only echoed when it matches `[A-Za-z0-9._-]{8,64}`.
  Client input reaches the log file, and a value containing newlines could
  otherwise forge log entries.
- Upload size is capped in the application (10 MB per file, 25 MB per request)
  and again at the gateway, so an oversized body is rejected before it reaches
  the JVM.

## 6. Data protection

Documents are stored in object storage; the database holds only metadata and a
storage key. The bucket is created by a provisioning step with anonymous access
set to none and versioning enabled, so the application never needs
bucket-creation rights of its own. In production
`dlp.storage.auto-create-bucket` is false.

## 7. Container hardening

Both images run as a non-root user. The backend runtime image contains a JRE and
curl only, with no build toolchain. Only the ports that must be reachable are
published: the API on 8080 and the portal on 3000. The management port, and
PostgreSQL, Redis and MinIO in a deployed environment, stay on the internal
network.


## 8. Authentication (Milestone 5)

Three actors sign in through one engine: bank users with an employee id and
password, customers with a mobile number and 6 digit PIN, field officers with
either. What differs is the credential; the mechanics of an attempt, a lock and
a session are identical, which is why there is one `auth.t_user` table.

**Secrets.** BCrypt at cost 12. Only the hash is stored, in
`auth.t_user_credential`, typed as PASSWORD or PIN so one identity can hold
both. No code path returns or logs a plaintext secret or an OTP.

**Tokens.** The access token is a HS256 JWT with a 15 minute life. It is not
revocable, which is why it is short. The refresh token is 32 bytes of
`SecureRandom`, stored only as a SHA-256 hash, so a reader of `auth.t_session`
cannot replay a session. Refresh rotates the token, so a stolen one stops
working the moment the real client next refreshes.

Signing is symmetric only because the platform is one deployable. Splitting the
API is the point at which this becomes RS256 with a published JWKS; the change
is confined to two beans in `AuthSecurityConfig`.

**Brute force.** Five consecutive failures lock an account for fifteen minutes,
both configurable. The counter lives in PostgreSQL, not Redis, so a lock
survives a cache flush.

The counter and the audit row are written in **their own transactions**. This
is not incidental: a rejected sign-in ends by throwing, and anything written in
that transaction is rolled back with it. Written inline, the lock would never
trigger and the trail would record only the attempts that succeeded. See
`AuthAttemptRecorder`.

**Enumeration.** Every failure returns one message. When no identity matches, a
hash is still verified against a dummy value so the unhappy path cannot be
timed to tell a real username from an invented one.

**Device binding.** A 6 digit PIN is weak on its own. It is accepted only from a
handset already promoted to `TRUSTED` by an OTP, which is what makes the pair
two factors rather than one short secret.

**Audit.** `auth.t_login_history` is append-only and records every attempt with
its outcome, reason, IP, user agent, device and correlation id - including
attempts that matched no identity, which is the pattern worth catching.

**Not yet done.** Rate limiting per IP and per account beyond the lock-out
counter; roles, permissions and scope claims, which are Milestone 6; and the
SMS gateway, so OTP delivery is unwired and a development flag returns the code
in the response. That flag must never be set anywhere a customer can reach.

## 9. Authorisation (Milestone 6)

Authentication answers who a caller is. This answers what they may do, and it
answers it from the database.

**A permission is a row.** `auth.t_permission` holds the catalogue,
`auth.t_role` the fourteen roles the specification names, and
`auth.t_role_permission` the grant between them. Changing what a Branch Manager
may do is an insert or a delete, never a deployment.

**Guards name permissions, not roles.** Every protected endpoint carries
`@PreAuthorize("hasAuthority('admin.role.view')")` or similar. Role codes are
also exposed as `ROLE_` authorities so `hasRole` works, but nothing in this
platform should use them: branching on a role name is exactly the hard-coding
the specification forbids, and it puts a bank policy decision back into the
source.

**Authority travels in the token.** An access token carries the permission codes
its holder had when it was issued, so an authorisation decision costs no
database read. The price is that a permission taken away is still honoured until
that token expires. Fifteen minutes is the ceiling on that window, and it is one
of the reasons the lifetime is short. A grant that must stop mattering
immediately is enforced by revoking the session, which the refresh token makes
possible; the next refresh resolves permissions afresh.

**Nothing is open by default.** The API chain still ends in
`anyRequest().authenticated()`, and the portal is closed at its route group, so
a new back-office page is protected by existing rather than by its author
remembering to guard it.

**Scope is organisational as well as functional.** A permission says what a
person may do; their role's `scope_level` and their postings say where. A
branch-scoped role reaches only the units it is posted to, a region-scoped role
reaches everything beneath them, and a head-office role reaches the bank.
Holding several roles grants the widest of them, because adding a role must
never take access away.

Two failure modes are guarded deliberately. An account posted nowhere resolves
to an empty set rather than to no filter, so it sees nothing instead of
everything. And descendants are resolved by a recursive query rather than a
stored path, because a path that drifts out of step with `parent_id` sends an
approval to the wrong region and nobody notices until it has.
Role and user administration screens are Milestone 35; the endpoints here are
reads only, because the audit trail that a permission change requires does not
exist yet.

### The permission catalogue as it stands

| Permission | Granted to | Guards |
| ---------- | ---------- | ------ |
| `system.health.view` | every staff role | The platform health endpoint |
| `admin.role.view` | ADMIN | Roles and permissions |
| `admin.user.view` | ADMIN | Bank users and their roles |
| `organization.view` | every staff role | The unit tree and the caller's own scope |
| `customer.view` | every staff role | The customer master |
| `product.view` | every staff role | The catalogue and the loan calculator |
| `eligibility.check` | every staff role | Running an eligibility assessment |
| `product.configure` | ADMIN | Drafting, amending, activating and retiring versions |
| `product.price` | ADMIN, HOCRM, CEO, MD | Quoting a rate other than the published one |
| `rules.view` | ADMIN, CA, HOCRM, CEO, MD, PPC | Reading the eligibility criteria |
| `rules.configure` | ADMIN | Reserved; no write endpoint exists yet |

Three of those are narrower than the rest, and deliberately.

**`product.configure`** decides what every subsequent application is judged by.
It sits with administration alone until the maker and checker of Milestone 21
exist to divide it, because one click is not the right amount of ceremony for
repricing a product.

**`product.price`** is a concession, and a concession is a credit decision, so it
goes to the roles that already carry delegated approval authority. It is checked
on one field of the request rather than on the endpoint - the calculator is open
to everyone who may see a product - and supplying a rate override without it is
refused rather than ignored. Quoting a customer a different rate from the one
asked for, silently, would be the worse failure.

**`rules.view`** goes to credit as well as to administration: anybody who has to
explain a decline needs to be able to read the rule that produced it.

`rules.configure` is granted but guards nothing yet. That is intentional and
visible rather than a permission invented later under time pressure; the write
endpoints arrive with maker-checker.

## 10. Known gaps

These are tracked, not overlooked:

1. `/api/v1/platform/health` is unauthenticated so the portal can render a system
   page before login. It exposes reachability only. It moves behind an admin
   permission in Milestone 5.
2. The development compose file publishes PostgreSQL, Redis and MinIO to
   localhost for debugging. A deployed environment must not.
3. TLS is terminated outside this stack. The gateway configuration is HTTP for
   local development only.
4. `.env.example` contains placeholder passwords. `dev-up` copies it to `.env` on
   first run and warns; change them on any shared machine.
