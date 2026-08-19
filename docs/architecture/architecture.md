# Architecture

## 1. Purpose

A configurable digital lending and account opening platform for a Bangladeshi
bank. It serves three client surfaces — a customer mobile app, a field officer
mobile app and a bank back-office portal — from a single authoritative backend.

## 2. Non-negotiable principles

### 2.1 The backend owns every decision

Flutter and Next.js are presentation layers. They must never contain
authoritative logic for eligibility, loan limits, interest, EMI, fees, approval
authority, workflow transitions, credit decisions, DPD, NPL classification,
repayment allocation or regulatory limits.

A client may show an indicative loan calculation for responsiveness, but the
figure that is stored, sanctioned and disbursed is the one the API returned.

### 2.2 Configuration over hard-coding

Product parameters, eligibility rules, fees, approval limits, workflow states and
transitions, and classification thresholds are **data**. A new loan product must
be introducible by an administrator, not by a release.

The concrete prohibition: no `if (role.equals("BM"))` and no
`if (amount <= 50000)` in service code. Both are lookups against configuration.

### 2.3 Product versioning

Every product is versioned. An application records `product_id` **and**
`product_version_id`, and is evaluated for its whole life under the version in
force when it was assessed. Live configuration is never edited in place.

### 2.4 Financial precision

`NUMERIC(20,4)` in PostgreSQL, `BigDecimal` in Java. `float` and `double` are
prohibited for money, rates and factors anywhere in the stack.

On the wire, decimals are serialised as **JSON strings**. A JavaScript client
parses a JSON number into a 64-bit float, which loses the precision the platform
is obliged to preserve. See `JacksonConfig`.

### 2.5 Modular monolith first

One Spring Boot deployable with clearly separated domain modules. Microservice
extraction is a later option that scale must justify, not a starting position.

### 2.6 PostgreSQL is authoritative

Customer, KYC, applications, loans, workflow state, approvals, repayments,
balances, collections, NPL state, audit and document metadata all live in
PostgreSQL. Redis holds only short-lived state and is always reconstructible.

## 3. Component view

```
   Customer app          Field Officer app          Bank portal (Next.js)
   (Flutter, M33)        (Flutter, M34)             server components + BFF routes
        |                       |                            |
        |  HTTPS /api/v1        |  HTTPS /api/v1             |  server-side fetch
        +-----------------------+----------------------------+
                                |
                    Nginx gateway (optional profile)
                                |
                  Spring Boot modular monolith (Java 21)
                                |
        +---------------+-------+--------+------------------+
        |               |                |                  |
   PostgreSQL         Redis          MinIO / S3      External providers
   authoritative   short-lived      document          CBS, CIB, e-KYC,
      state           state          binaries         screening, MFS, SMS
```

The browser never addresses Spring Boot directly. The portal calls its own
route handlers under `/bff/**`, which reach the API server-side. That keeps the
API off the public origin and removes CORS between portal and API. CORS remains
configured for the mobile clients.

## 4. Backend module layout

Packages under `com.naztech.lending`:

| Module | Responsibility | Milestone |
| ------ | -------------- | --------- |
| `common` | Response envelope, errors, correlation id | 1 |
| `config` | Cross-cutting Spring configuration | 1 |
| `platform` | Environment identity and connectivity | 1 |
| `storage` | S3-compatible document storage client | 1 |
| `security` | Authentication, RBAC, method security | 5-6 |
| `auth` | Login, OTP, PIN, device binding, sessions | 5 |
| `organization` | Bank, zone, region, branch, unit hierarchy | 7 |
| `customer` | Customer master and financial profile | 8 |
| `document` | Document metadata, verification, versioning | 9 |
| `kyc` | KYC and e-KYC provider abstraction | 10 |
| `account` | Account products and account opening | 11-12 |
| `product` | Loan products and product versions | 13-14 |
| `rules` | Generic rule engine | 15 |
| `eligibility` | Eligibility and loan amount engines | 16-17 |
| `pricing` | Interest, fees, EMI and the loan calculator | 16-17 |
| `application` | Loan applications and their lifecycle | 18 |
| `workflow` | States, transitions, role-state permissions | 19 |
| `credit` | Credit analysis and scorecards | 20 |
| `cib` | CIB provider abstraction | 21 |
| `screening` | Sanctions, PEP, blacklist, duplicates | 22 |
| `approval` | Approval matrix, conditions, group approval | 23-25 |
| `loan` | Disbursed loans, schedules, balances | 26 |
| `repayment` | Payments, allocation, reconciliation | 28 |
| `collection` | DPD buckets, queues, recovery | 29-31 |
| `npl` | Classification and alerting | 30 |
| `notification` | Templates, events, queue, delivery | 32 |
| `integration` | Provider interfaces, outbox, reconciliation | throughout |
| `reporting` | Origination, credit, portfolio reporting | 39 |
| `audit` | Immutable audit trail | 40 |

Each module uses the separation that its size warrants:
`controller / service / domain / repository / dto / mapper / validator /
exception`. Abstraction is added when a second implementation exists, not in
anticipation of one.

### Module boundaries

A module owns exactly one PostgreSQL schema. Reaching into the tables of another
module from a repository is not allowed; go through that service instead. The
boundary is enforced by convention and code review today, and it is what makes a
later service extraction feasible.

Two modules own no schema, which is not an exception to that rule but a
consequence of it: `pricing` computes and stores nothing, and `eligibility`
orchestrates two engines that each keep their own records elsewhere. A module
gets a schema when it has state, not because it exists.

`eligibility` is also where the customer and rules modules meet. The rules module
must not import the customer module - it would then be a rules-about-customers
module, and the next subject type would need a second copy of it - and the
customer module has no business knowing rules exist. So the bridge lives in the
one place that actually wants both.

## 5. Request flow

1. `CorrelationIdFilter` binds a correlation id, taking the one supplied by the
   caller when it is safe to echo and generating a fresh one otherwise. It goes
   into the MDC, so every log line for the request carries it, and it is
   returned as `X-Correlation-Id`.
2. Spring Security authorises the request. The API is closed by default: a new
   endpoint is unreachable until an authorisation rule is added for it.
3. The controller validates the request DTO and delegates. Controllers never
   expose JPA entities.
4. The service applies business rules, all of which are driven by configuration.
5. `GlobalExceptionHandler` converts any failure into the standard envelope.
   Internal detail is logged with the correlation id and never returned.

## 6. Cross-cutting decisions

| Decision | Rationale |
| -------- | --------- |
| Actuator on its own port (9091), unpublished | Metrics and health detail must not be reachable from outside the container network |
| API closed by default (`anyRequest().authenticated()`) | A forgotten authorisation rule fails shut, not open |
| Decimals serialised as strings | A JavaScript client cannot silently round a money value |
| Redis values serialised as JSON | JDK serialisation is opaque and a deserialisation risk |
| Flyway owns the schema, Hibernate set to `validate` | The schema has exactly one owner |
| `open-in-view: false` | No lazy loading outside a transaction; queries stay visible |
| Errors returned as values in the portal | A dependency being down must render, not crash the page |

## 7. External integration

Every external system sits behind an interface with a mock implementation for
development: `CBSProvider`, `IdentityVerificationProvider`, `CIBProvider`,
`SanctionScreeningProvider`, `PoliceVerificationProvider`, `MfsProvider`,
`PaymentProvider`, `SmsProvider`, `EmailProvider`.

Each integration must support timeout, retry, idempotency key, request id,
correlation id, error handling and reconciliation. An external failure must never
leave the transactional state corrupt, which is why anything that changes state
and then calls outward uses the outbox pattern: commit the state change and an
outbox row in one transaction, then let a worker perform the call and record the
result.

Kafka is deliberately absent. The outbox is a database table until volume
justifies a broker.

## 8. What Milestone 1 delivered

- Monorepo layout, Docker Compose stack, helper scripts
- Spring Boot 3.5 on Java 21: envelope, error handling, correlation ids,
  security baseline, OpenAPI, Redis and object storage clients, health endpoints
- Flyway baseline creating the eighteen module schemas, and nothing else
- Next.js portal with a system health page that exercises the whole vertical
- 33 unit tests and 14 Testcontainers integration tests

## 9. What Milestones 5 to 19 added

- **5-6** Authentication and authorisation: sign-in, MFA, session rotation, the
  login audit trail, fourteen roles and a permission catalogue
- **7** The bank's own hierarchy, as one configurable tree, and the scope rules
  that follow from it
- **8** The customer master, read through those scopes
- **13-14** The product catalogue and its versioning: terms live on a version,
  a live version is never edited, and repricing means issuing a new one
- **15** A generic rule engine - attribute, operator, value - with every
  evaluation recorded against the product version it was decided under
- **16-17** The eligibility engine, the loan amount engine that reports which of
  seven caps bound the result, and the pricing calculator behind both
- **18-19** The loan application - a snapshot of the terms, the applicant and the
  quotation it was judged on - and the six-step workflow, whose states,
  transitions and role permissions are three tables rather than any Java

## 10. What is deliberately absent

Credit analysis, CIB and screening, the sanctioning-limit matrix, disbursement to
core banking, repayment, collections and the Flutter applications. Each arrives
in its own milestone. No speculative tables, no speculative abstractions and no
dependencies without a current use.
