# Development setup

There are two supported ways to run the platform locally. Both target the same
Linux deployment, and CI always runs path A.

| | Path A: containers | Path B: native Windows services |
| --- | --- | --- |
| Infrastructure | Docker Compose | PostgreSQL, Memurai, MinIO installed on Windows |
| Applications | Built in containers | Run from your IDE or the command line |
| `docker compose up` | Yes | Not applicable |
| Testcontainers integration tests | Run | Skipped, with a reason |
| Monitoring profile | Yes | Not applicable |

Path B exists because PostgreSQL, Redis, MinIO and Nginx publish **Linux images
only**. Docker Desktop in Windows-containers mode cannot run any of them, so a
workstation that will not run a Linux container engine uses native services
instead. It changes nothing about how the platform is built or deployed.

## 1. Prerequisites

| Tool | Version | Needed for |
| ---- | ------- | ---------- |
| Docker Desktop | Compose v2.20 or newer | Path A, and the integration tests |
| JDK | 21 | Building the backend outside a container. `JAVA_HOME` must point at it |
| Maven | 3.9 or newer | Building the backend outside a container |
| Node.js | 20.9 or newer | Building the portal outside a container |

On path A only Docker is strictly required: the compose stack builds both
applications in containers, so a workstation without a JDK or Node can still run
everything. Path B needs the JDK, Maven and Node.

Compose v2.20 is the floor because the root `docker-compose.yml` uses `include`.
On an older version, run the stack file directly:

```bash
docker compose -f infrastructure/docker/docker-compose.yml --env-file .env up -d
```

## 2. Path A: first run with containers

```bash
cp .env.example .env
```

```bash
docker compose up -d
```

The first build takes several minutes while Maven and npm dependencies download.
Afterwards both are layer-cached.

Check progress:

```bash
docker compose ps
```

All services should reach `healthy`. Then open
`http://localhost:3000/system/health` — three green components confirm the whole
vertical: Next.js reached Spring Boot, which reached PostgreSQL, Redis and MinIO.

On Windows the helper scripts do the same thing:

```powershell
.\infrastructure\scripts\dev-up.ps1
```

## 3. Local URLs

| Surface | URL |
| ------- | --- |
| Bank portal | http://localhost:3000 |
| System health page | http://localhost:3000/system/health |
| API connectivity | http://localhost:8080/api/v1/platform/health |
| API identity | http://localhost:8080/api/v1/platform/info |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI document | http://localhost:8080/v3/api-docs |
| MinIO console | http://localhost:9001 |
| PostgreSQL | localhost:5432 |
| Redis | localhost:6379 |

With `--profile monitoring`: Prometheus on 9090 and Grafana on 3001.

Actuator is on port 9091 inside the network only, and is intentionally not
published or proxied.

## 4. Path B: native Windows services

Install the three services once, from an **elevated** PowerShell. They register
Windows services, so the elevation prompt is unavoidable.

```powershell
winget install --id PostgreSQL.PostgreSQL.17 -e --accept-package-agreements --accept-source-agreements
```

```powershell
winget install --id Memurai.MemuraiDeveloper -e --accept-package-agreements --accept-source-agreements
```

```powershell
winget install --id MinIO.Server -e --accept-package-agreements --accept-source-agreements
```

Memurai is a Redis-compatible server built for Windows. Redis itself publishes no
official Windows build, and production runs real Redis on Linux; the wire
protocol is the same, so the application code does not know the difference.

Then bootstrap the database and object storage. The script prompts for the
PostgreSQL superuser password you chose during installation, creates the
`digital_lending` database and its `dlp_owner` role, and applies the same
`01-initialise-database.sql` the Docker path applies:

```powershell
.\infrastructure\scripts\win-dev-setup.ps1
```

Start MinIO, which is a plain executable rather than a service, and check
everything is reachable:

```powershell
.\infrastructure\scripts\win-dev-services.ps1 -Start
```

```powershell
.\infrastructure\scripts\win-dev-services.ps1 -Status
```

The credentials the scripts use match the defaults in `application.yml`, so the
backend and portal start with no environment variables set. They are local
development values and are not secrets.

Finally run the two applications, as in the next section. Flyway creates the
eighteen schemas on first backend startup, exactly as it does under Docker.

## 5. Running the applications outside a container

On path A, start only the infrastructure and run each application from your IDE:

```bash
docker compose up -d postgres redis minio minio-init
```

On path B the services are already running; skip straight to the commands below.

Backend, using the `local` profile which points at localhost:

```bash
mvn -f backend/digital-lending-api/pom.xml spring-boot:run
```

Credentials come from the environment. Export the same values you put in `.env`,
or set them in the run configuration:
`DLP_DB_PASSWORD`, `DLP_REDIS_PASSWORD`, `DLP_STORAGE_ACCESS_KEY`,
`DLP_STORAGE_SECRET_KEY`.

Portal:

```bash
npm --prefix web/bank-portal run dev
```

Create `web/bank-portal/.env.local` from `.env.example` first, so
`BACKEND_INTERNAL_URL` points at `http://localhost:8080`.

## 6. Everyday commands

```bash
docker compose logs -f backend
```

```bash
docker compose restart backend
```

Rebuild after changing application code:

```bash
docker compose up -d --build backend
```

Start over with an empty database, which also re-verifies that migrations apply
cleanly from scratch:

```bash
./infrastructure/scripts/dev-reset.sh
```

## 7. Adding a database change

1. Add `V<n>__<description>.sql` under
   `backend/digital-lending-api/src/main/resources/db/migration`.
2. Restart the backend; Flyway applies it.
3. Run `dev-reset` at least once before opening a pull request, to prove the
   whole set still applies to an empty database.

Never edit an applied migration. Correct it with a new one.

## 8. Troubleshooting

| Symptom | Cause and fix |
| ------- | ------------- |
| `error: release version 21 not supported` | `JAVA_HOME` points at an older JDK. Maven follows `JAVA_HOME`, not PATH, so having `java 21` on PATH is not enough. Check with `echo $env:JAVA_HOME` and point it at a JDK 21 installation |
| `POSTGRES_PASSWORD must be set` | `.env` is missing; copy it from `.env.example` |
| Backend restarts repeatedly | Check `docker compose logs backend`; usually a database credential mismatch after changing `.env` without recreating the volume |
| `include` is not a valid compose key | Compose is older than v2.20; use the `-f infrastructure/docker/...` form above |
| Integration tests skipped | No Docker daemon; the skip reason says so |
| Port already allocated | Change the port in `.env`; every published port is configurable |
| Health page shows objectStorage DOWN | Path A: `minio-init` did not complete, check `docker compose logs minio-init`. Path B: MinIO is not running, use `win-dev-services.ps1 -Start` |
| Docker Desktop in Windows-containers mode pulls nothing | PostgreSQL, Redis, MinIO and Nginx publish Linux images only. Switch back to Linux containers, or use path B |
| `psql was not found` from `win-dev-setup.ps1` | PostgreSQL is not installed, or was installed somewhere other than `%ProgramFiles%\PostgreSQL`. Add its `bin` directory to PATH |
| Backend starts but Flyway fails with a permission error | The database exists but is not owned by `dlp_owner`. Drop it and re-run `win-dev-setup.ps1` |
