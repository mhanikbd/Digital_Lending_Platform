# Database design

## 1. Ownership

PostgreSQL is the authoritative store. Flyway owns every object in it. Hibernate
runs with `ddl-auto: validate` and can never alter the schema.

Migrations live in
`backend/digital-lending-api/src/main/resources/db/migration` and are applied on
application startup.

| Rule | Detail |
| ---- | ------ |
| One migration per change | No undocumented schema modification, ever |
| Forward only | An applied migration is immutable; correct it with a new one |
| Clean-database check | `dev-reset` recreates the volume so the full set replays |
| No out-of-order application | `spring.flyway.out-of-order: false` |

Naming: `V<n>__<snake_case_description>.sql`. Repeatable migrations use the `R__`
prefix and are reserved for views and functions.

## 2. Schema layout

One logical schema per backend module, so the module boundary is visible in the
database. A repository may only read and write its own schema; anything else
goes through the owning service.

| Schema | Contents |
| ------ | -------- |
| `auth` | Users, roles, permissions, devices, sessions, login history |
| `organization` | Bank, zone, region, branch, department, business unit, credit unit |
| `customer` | Customer master, addresses, contacts, employment, financial profile |
| `kyc` | KYC and e-KYC verification records and provider responses |
| `account` | Account products, account opening applications, opened accounts |
| `document` | Document metadata and verification state |
| `product` | Loan products, versions, parameters, fees, required documents |
| `rules` | Rule definitions, groups, operators, evaluation results |
| `application` | Loan applications, applicants, financials, purposes, queries |
| `workflow` | States, transitions, role-state permissions, state history |
| `credit` | Credit analysis, scorecards, CIB records, screening, risk grades |
| `approval` | Approval matrix, tiers, limits, delegation, conditions, groups |
| `loan` | Disbursed loans, schedules, balances, charges, settlement, closure |
| `repayment` | Repayment transactions, allocation, reversals, reconciliation |
| `collection` | DPD buckets, collection queue, contact history, recovery |
| `notification` | Templates, events, queue, delivery log |
| `integration` | Outbox events, external request and response records |
| `audit` | Immutable audit trail across all modules |

`V1__create_platform_schemas.sql` creates all eighteen with a `COMMENT ON SCHEMA`
describing the ownership, and **no tables**. Tables arrive with the migration
that introduces the module needing them, which keeps the schema honest about
what is actually implemented.

## 3. Conventions

### Tables and columns

- Tables are prefixed `t_` and named in the singular: `customer.t_customer`.
- Columns are `snake_case`.
- Every table carries `created_at`, `created_by`, `updated_at`, `updated_by`.
- Timestamps are `TIMESTAMPTZ` and stored in UTC. The database is set to UTC by
  the cluster init script; local time is a presentation concern.
- Soft deletion is used only where an audit requirement demands it, never as a
  default.

### Money and rates

```
amount        NUMERIC(20,4)  NOT NULL
interest_rate NUMERIC(9,6)   NOT NULL
```

`NUMERIC` only. No `float`, `double precision`, `real` or `money`. The Java side
is `BigDecimal`, and the JSON side is a decimal string.

### Keys

- Surrogate primary keys.
- Business identifiers such as application number and loan number are separate,
  human-readable, and carry a unique constraint.
- Foreign keys are declared. Referential integrity belongs in the database, not
  only in the service layer.

### Product version pinning

Any table recording an assessment or decision stores both `product_id` and
`product_version_id`. Re-reading an old application must reproduce the rules it
was actually judged under.

## 4. Connection management

HikariCP, configured in `application.yml`:

| Setting | Development | Reason |
| ------- | ----------- | ------ |
| `maximum-pool-size` | 20 | Sized against the PostgreSQL connection limit |
| `minimum-idle` | 5 | Avoids cold-start latency |
| `connection-timeout` | 30s | Fail rather than queue indefinitely |
| `leak-detection-threshold` | 60s | Surfaces a connection that is never returned |

The cluster init script also sets `lock_timeout` to 10s and
`idle_in_transaction_session_timeout` to 60s, so a stuck transaction cannot hold
a lock through a migration or a business day.

## 5. Current state

After Milestone 1 the database contains the eighteen schemas, and
`public.flyway_schema_history`. `DatabaseMigrationIT` asserts exactly that,
including that no business tables exist yet, so a speculative table cannot be
added without the test noticing.
