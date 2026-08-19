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

Migrations create tables only when the module that needs them arrives, so most
of the eighteen schemas are still empty by design.

`DatabaseMigrationIT` pins this: it asserts the exact set of tables each
schema holds, and that every schema whose milestone has not arrived holds none.
Adding a table means adding a line to that test deliberately, which is the
moment to ask whether it is speculative.

| Migration | Schema | Tables |
| --------- | ------ | ------ |
| V1 | all eighteen | none, deliberately |
| V2 | `auth` | `t_user`, `t_user_credential`, `t_device`, `t_session`, `t_login_history` |
| V3 | `auth` | `t_permission`, `t_role`, `t_role_permission`, `t_user_role` |
| V4 | `organization` | `t_org_unit_type`, `t_org_unit`, `t_user_org_unit` |
| V5 | `customer` | `t_customer`, `t_customer_address`, `t_customer_identification` |
| V6 | `product` | `t_loan_product`, `t_loan_product_version`, `t_product_tenure`, `t_product_fee`, `t_product_risk_limit` |
| V7 | `rules` | `t_rule_attribute`, `t_rule_group`, `t_rule`, `t_rule_evaluation`, `t_rule_evaluation_detail` |
| V8 | `workflow` | `t_workflow_state`, `t_role_state_map`, `t_state_transition` |
| V9 | `application` | `t_loan_purpose`, `t_loan_application`, `t_loan_application_applicant`, `t_loan_application_financial`, `t_loan_application_document`, `t_loan_application_status_history`, `t_loan_application_comment`, `t_loan_application_query`, `t_loan_application_query_response` |

### Modelling decisions worth knowing

**One organisation table, not nine.** The specification names bank, zone,
region, branch, department, business unit, credit unit, personal processing
centre and credit administration department. They are one self-referencing
table with a type, because every one of them is a named node with a parent, and
a bank opening a tenth kind should not need a migration.

**Identification documents are rows, not columns.** A passport has an issue
date, an expiry, a place of issue and a number; a driving licence has an expiry;
a TIN has neither. Four of those flattened onto the customer row is a row that
is mostly null and a form that has to know which columns belong together.

**Descendants are queried, not stored.** `t_org_unit` keeps `parent_id` and
nothing else about position. Subtrees come from a recursive query. A stored
path column would have to be rewritten for an entire subtree whenever a unit
moved, and a path that drifts out of step with `parent_id` misroutes approvals
silently.

**A product holds nothing that gets repriced.** The rate, the amount bounds, the
tenures, the fees and the limit parameters are all on `t_loan_product_version`.
There is no rate column on `t_loan_product` to edit by accident, and a partial
unique index - `ON (product_id) WHERE status = 'ACTIVE'` - permits exactly one
live version, so "which terms apply" cannot have two answers.

**A rule's comparison value is text.** One column, parsed according to the
attribute's declared type. Four typed columns would be three nulls per row; a
JSON blob would be unqueryable. `IN` reads it as a comma separated list, and
`BETWEEN` takes the upper bound from `comparison_value2` - which a check
constraint requires for `BETWEEN` and forbids for everything else.

**An evaluation's detail rows carry codes, not foreign keys.** A rule may be
edited or deleted after a decision was made against it. A reason that changes
when somebody retunes the criteria is not a reason, so the group code, attribute
code, operator and expected value are copied into the record and the record stops
depending on the configuration surviving.

**An application copies rather than points.** `t_loan_application` records the
product version, the rate and the quotation; `t_loan_application_applicant` and
`_financial` copy the customer as declared on the day. A decision taken on last
year's facts has to keep showing last year's facts, and a product repriced next
month must not change the basis of one already taken.

**The status history is text, not foreign keys.** The user who moved a file may
later be deleted and the state may later be retired. A history that can be
emptied by a tidy-up is not a history, so the actor, the role and both states are
copied into the row.

**A transition can carry a role.** `t_state_transition.actor_role_code` exists
because three roles RECOMMEND from the same state into three different
destinations. Null means the move is open to whoever the role/state map allows,
which is almost all of them - and putting the answer in the row rather than in
the engine is what makes a fourth branch role an INSERT.

**Exposure ceilings are nullable and unset.** `max_total_exposure` caps what a
borrower may owe in total. Left null for e-Loan on purpose: the product's own
maximum is what *this* product lends, not what the borrower may owe altogether,
and treating it as a total-debt ceiling would refuse a small personal loan to
anybody holding a mortgage. Affordability is `max_dbr`'s job.
