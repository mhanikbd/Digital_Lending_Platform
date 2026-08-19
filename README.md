# Digital Lending Platform

A configurable, bank-grade digital lending and account opening platform. The
first lending product is **e-Loan**; further products (Quick, Instant, Personal,
Car, Student, Home, SME/CMSME, Credit Card) are introduced through configuration
and product versioning rather than new codebases.
> **Current state: Milestone 1 — platform foundation.**
> Infrastructure, the Spring Boot API skeleton, database migrations and the bank
> portal are in place. No lending business functionality is implemented yet.

## Repository layout

```
digital-lending-platform/
├── backend/digital-lending-api/   Spring Boot modular monolith (Java 21)
├── web/bank-portal/               Next.js back-office portal (TypeScript)
├── mobile/                        Flutter apps — deferred, see mobile/README.md
├── infrastructure/
│   ├── docker/                    Compose stack definition
│   ├── postgres/                  Cluster initialisation
│   ├── nginx/                     API gateway (optional profile)
│   ├── monitoring/                Prometheus, Grafana, Loki, Promtail
│   └── scripts/                   Development helper scripts
├── docs/                          Architecture, database, API, security, testing
└── docker-compose.yml             Entry point for the whole stack
```

## Prerequisites

| Tool           | Version | Needed for                                  |
| -------------- | ------- | ------------------------------------------- |
| Docker Desktop | Compose v2.20+ | Running the stack, and integration tests |
| JDK            | 21      | Building the backend outside Docker         |
| Maven          | 3.9+    | Building the backend outside Docker         |
| Node.js        | 20.9+   | Building the portal outside Docker          |

Everything except Docker is optional: the compose stack builds both applications
in containers.

If you would rather not run a Linux container engine, PostgreSQL, Memurai and
MinIO can be installed as native Windows services instead. PostgreSQL, Redis,
MinIO and Nginx publish Linux images only, so Windows-containers mode is not an
option. See [development setup](docs/deployment/development-setup.md) for both
paths; the deployment target is Linux either way.

## Getting started

```bash
cp .env.example .env
docker compose up -d
```

Then open:

| Surface                | URL                                              |
| ---------------------- | ------------------------------------------------ |
| Bank portal            | http://localhost:3000                             |
| System health page     | http://localhost:3000/system/health               |
| API connectivity       | http://localhost:8080/api/v1/platform/health      |
| Swagger UI             | http://localhost:8080/swagger-ui.html             |
| MinIO console          | http://localhost:9001                             |

Optional profiles:

```bash
docker compose --profile gateway up -d
```

```bash
docker compose --profile monitoring up -d
```

Helper scripts live in `infrastructure/scripts/` (`dev-up`, `dev-down`,
`dev-logs`, `dev-reset`, in both PowerShell and shell form).

## Building and testing without Docker

Backend unit tests — no Docker required:

```bash
mvn -f backend/digital-lending-api/pom.xml test
```

Backend integration tests — starts PostgreSQL, Redis and MinIO through
Testcontainers, and is skipped with a reason when no Docker daemon is present:

```bash
mvn -f backend/digital-lending-api/pom.xml verify
```

Portal:

```bash
npm --prefix web/bank-portal run build
```

## Architecture in one page

- **The backend is authoritative.** Eligibility, limits, interest, EMI, fees,
  approval authority, workflow transitions, DPD, classification and repayment
  allocation are decided in Spring Boot. Clients render decisions; they never
  make them.
- **Configuration over code.** Products, versions, rules, fees, workflow states,
  approval matrices and classification thresholds are data, not Java branches.
- **Modular monolith.** One deployable, with a schema per domain module so the
  boundaries stay visible and a later extraction remains possible.
- **Flyway owns the schema.** Hibernate is restricted to `validate`.
- **Money is exact.** `NUMERIC(20,4)` in PostgreSQL, `BigDecimal` in Java, and
  decimal **strings** on the wire so JavaScript clients cannot round them.

## Documentation

| Document | Contents |
| -------- | -------- |
| [Architecture](docs/architecture/architecture.md) | Modules, boundaries, request flow, decisions |
| [Database design](docs/database/database-design.md) | Schema layout, migration and money conventions |
| [API specification](docs/api/api-specification.md) | Envelope, errors, correlation ids, endpoints |
| [Security](docs/security/security.md) | Current posture and what each milestone adds |
| [Deployment](docs/deployment/deployment.md) | Compose stack, images, configuration, profiles |
| [Development setup](docs/deployment/development-setup.md) | Getting a workstation running |
| [Testing](docs/testing/testing.md) | Test layers, how to run them, what is covered |
| [Workflow](docs/workflows/workflow.md) | The six-step loan workflow (design intent) |
| [Product configuration](docs/product/product-configuration.md) | Product and version model (design intent) |

## Milestone progress

| # | Milestone | Status |
| - | --------- | ------ |
| 1 | Repository, Docker, PostgreSQL, Flyway, Spring Boot, Next.js foundation | Complete |
| 2–4 | Infrastructure, database and backend foundation | Folded into Milestone 1 |
| 5 | Authentication | Next |
| 6–44 | RBAC through production deployment | Not started |

Flutter applications (milestones 33 and 34) are deferred by decision; see
`mobile/README.md`.
