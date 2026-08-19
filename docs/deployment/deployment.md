# Deployment

## 1. Stack

Defined in `infrastructure/docker/docker-compose.yml`, included by the root
`docker-compose.yml` so the platform starts with `docker compose up` from the
repository root.

| Service | Image | Published | Profile |
| ------- | ----- | --------- | ------- |
| `postgres` | `postgres:17-alpine` | 5432 (dev only) | default |
| `redis` | `redis:7-alpine` | 6379 (dev only) | default |
| `minio` | `minio/minio:RELEASE.2025-09-07T16-13-09Z` | 9000, 9001 (dev only) | default |
| `minio-init` | `minio/mc:RELEASE.2025-08-13T08-35-41Z` | none, runs once | default |
| `backend` | built from `backend/digital-lending-api` | 8080 | default |
| `web` | built from `web/bank-portal` | 3000 | default |
| `nginx` | `nginx:1.29-alpine` | 80 | `gateway` |
| `prometheus` | `prom/prometheus:v3.14.0` | 9090 | `monitoring` |
| `loki` | `grafana/loki:3.6.15` | none | `monitoring` |
| `promtail` | `grafana/promtail:3.6.11` | none | `monitoring` |
| `grafana` | `grafana/grafana:12.4.8` | 3001 | `monitoring` |

Tags are pinned. The PostgreSQL, Redis and MinIO tags must be kept in step with
`support/PlatformContainers.java`, so tests exercise the deployed versions.

## 2. Startup order

`depends_on` with health conditions, so nothing starts against a dependency that
is not ready:

```
postgres (healthy) ─┐
redis    (healthy) ─┼─→ backend (healthy) ─→ web
minio    (healthy) ─┤
minio-init (completed successfully) ─┘
```

`minio-init` provisions the document bucket, disables anonymous access and
enables versioning, then exits. The backend therefore runs with
`DLP_STORAGE_AUTO_CREATE_BUCKET=false`, exactly as it will in production, and
never needs bucket-creation rights.

## 3. Images

**Backend** — multi-stage. `maven:3.9-eclipse-temurin-21` resolves dependencies
in their own layer and packages the jar; `eclipse-temurin:21-jre-alpine` runs it
as a non-root user with curl for the healthcheck. Tests are skipped during the
image build because integration tests need a Docker daemon, which is not
available inside one; the pipeline runs `mvn verify` before an image is built.

**Portal** — three stages ending in the Next.js standalone output, which ships
only the server and the modules it imports, run as a non-root user.

## 4. Configuration

All configuration is environment variables, resolved in `application.yml`.
Profiles:

| Profile | Use | Behaviour |
| ------- | --- | --------- |
| `local` | Workstation, dependencies on localhost | SQL logging on |
| `docker` | Compose stack | Service-name hosts, ECS JSON logs |
| `test` | Integration tests | Endpoints injected by Testcontainers |
| `prod` | Deployed | No defaults, OpenAPI off, health detail hidden, WARN logging |

The `prod` profile deliberately has no fallback values. A missing secret fails
startup rather than silently using a development default.

## 5. Ports and exposure

Only 8080 and 3000 are published for the applications. Actuator is bound to 9091
and is neither published by compose nor proxied by Nginx, so metrics and health
detail stay inside the container network where Prometheus scrapes them.

The development stack also publishes PostgreSQL, Redis and MinIO to localhost for
debugging. **A deployed environment must not.** Remove those `ports:` entries, or
override them, in the environment-specific compose file.

## 6. Gateway profile

```bash
docker compose --profile gateway up -d
```

Nginx puts the portal and the API on one origin: `/api/` to the backend and
everything else to the portal. It also caps request bodies at 25 MB and logs the
correlation id on every line, so gateway logs join to application logs.

In a deployed environment TLS terminates here, and HSTS is already sent by the
backend.

## 7. Monitoring profile

```bash
docker compose --profile monitoring up -d
```

Prometheus scrapes `backend:9091/actuator/prometheus` every 15 seconds. In the
`docker` profile the backend emits ECS JSON on stdout, so Promtail ships
structured fields, including `correlationId`, to Loki. Grafana is provisioned
with both datasources and requires a password from `.env`; anonymous access and
sign-up are disabled.

## 8. Going to production

Not yet in scope — Milestone 44 — but the shape is already fixed:

1. Build images in CI after `mvn verify` passes.
2. Supply every secret from the platform secret store; the `prod` profile has no
   defaults.
3. Terminate TLS at the gateway or load balancer.
4. Run PostgreSQL as a managed instance with point-in-time recovery.
5. Provision object storage buckets ahead of deployment with their retention and
   access policies.
6. Stop publishing datastore ports.
7. Apply migrations by starting one backend instance before scaling out, so
   concurrent instances never race on Flyway.
