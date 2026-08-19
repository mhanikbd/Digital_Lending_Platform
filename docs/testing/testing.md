# Testing

## 1. Layers

| Layer | Naming | Runner | Docker | Runs on |
| ----- | ------ | ------ | ------ | ------- |
| Unit and slice | `*Test.java` | Surefire | Not required | Every build |
| Integration | `*IT.java` | Failsafe | Required | `mvn verify` and CI |

The split is deliberate. A developer without a Docker daemon still gets a
meaningful test run, and CI, which always has one, executes everything.

Where a behaviour can be checked without infrastructure it is checked in the unit
layer, even when an integration test also covers it end to end. Redis
serialisation and configuration binding are both verified that way, because a
mistake in either surfaces as a context-startup failure that is otherwise only
reachable with Docker running.
Integration tests are skipped with a stated reason rather than failing when no
daemon is present.

## 2. Running them

```bash
mvn -f backend/digital-lending-api/pom.xml test
```

```bash
mvn -f backend/digital-lending-api/pom.xml verify
```

```bash
npm --prefix web/bank-portal run build
```

## 3. Integration test infrastructure

`support/PlatformContainers` starts PostgreSQL, Redis and MinIO once for the
whole build and shares them across test classes. Image tags are pinned there and
must match `infrastructure/docker/docker-compose.yml`: a test is only meaningful
if it runs against the version the platform actually deploys.

`support/IntegrationTestBase` wires the container endpoints in through
`@DynamicPropertySource` and gates execution on Docker availability. The gate is
a JUnit condition rather than a Testcontainers one, so the container holder class
is never even loaded on a machine without Docker.

The gate is applied through `@RequiresDocker`, not a bare `@EnabledIf`. JUnit
does not treat `@EnabledIf` as inherited, so putting it straight on the base
class does nothing for the subclasses: they run anyway and fail on container
startup instead of skipping. `@RequiresDocker` is `@Inherited` and meta-annotated
with the condition, and `IntegrationTestBaseAnnotationTest` asserts that a
subclass really does resolve it.

## 4. Coverage today

**Unit — 33 tests**

| Class | Covers |
| ----- | ------ |
| `CorrelationIdFilterTest` | Generation, propagation, rejection of log-injection and short values, cleanup on both the success and exception paths |
| `ApiResponseTest` | Envelope shape, correlation id stamping, defensive copying of violations |
| `GlobalExceptionHandlerTest` | Validation, business rule, not found, method, media type and malformed body mapping, and that internal detail never reaches the client |
| `ObjectStorageHealthIndicatorTest` | Bucket reachable, bucket missing, store unreachable without leaking credentials |
| `PlatformHealthServiceTest` | Aggregation, and that one failing dependency does not mask the state of the others |
| `RedisConfigTest` | JSON rather than JDK serialisation, string keys, no embedded type metadata, and that structured values read back as maps |
| `ConfigurationPropertiesBindingTest` | That comma-separated environment variables bind to list properties, which otherwise only fails at context startup |
| `IntegrationTestBaseAnnotationTest` | That the Docker skip condition actually reaches subclasses, and that every `*IT` extends the base |

**Integration — 14 tests**

| Class | Covers |
| ----- | ------ |
| `DatabaseMigrationIT` | All eighteen schemas created on a clean database, every schema documented, baseline recorded by Flyway, and no business tables yet |
| `CacheConnectivityIT` | Set and get, TTL behaviour, and that values are stored as JSON rather than JDK serialisation |
| `ObjectStorageConnectivityIT` | Bucket provisioning, and a put, stat, get, remove round trip |
| `PlatformEndpointsIT` | Health and info over HTTP, correlation id echo, that every other endpoint is closed, and that the OpenAPI document is published |

## 5. What each later milestone must add

Calculation and workflow correctness is where the risk in this platform lives, so
these are not optional:

- **Eligibility** — pass, fail, and each individual rule
- **Loan amount** — minimum, maximum, and which constraint bound the result
- **Calculator** — EMI and interest for flat, reducing balance and effective
  rate, plus rounding at each boundary
- **Workflow** — forward transitions, returns, and that an unauthorised
  role-state transition is refused
- **Branch approval** — BM, BOM and PPC recommendation paths
- **Credit analysis** — query raised, query answered, return
- **Approval** — each tier, escalation, conditional approval, group approval
- **CBS** — success, timeout, failure, and a duplicate response arriving twice
- **Payments** — success, failure, reversal, duplicate callback
- **DPD and NPL** — day count, classification thresholds, alert generation

Every one of these is configuration-driven, so each test must set up its own
product version and rule set rather than depending on seed data.

## 6. Definition of done

A milestone is complete only when the code compiles, migrations succeed from a
clean database, unit and integration tests pass, APIs are documented,
authorisation is enforced, audit records are written where required, error
handling exists, the frontend talks to real APIs, documentation is updated and
the Docker environment works.

Do not start the next milestone with a failing test.

## 7. Performance testing

Planned for Milestone 43 with k6, covering login, eligibility, calculator,
application submission, dashboard, loan search, approval, repayment and the
collection queue. Targets are set and recorded when the endpoints exist; setting
them now would be guesswork.
