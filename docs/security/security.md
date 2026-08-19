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

## 9. Known gaps

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
