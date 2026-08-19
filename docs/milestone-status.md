# Milestone progress status

**As at 20 August 2026** · Digital Lending Platform for NRBC Bank · built by naztech

Updated on every commit. The specification lists 44 milestones across ten phases;
this records exactly where each one stands and, where something is deliberately
incomplete, why.

---

## Summary

| | Milestones | Status |
| --- | --- | --- |
| **Complete** | 1–8, 13–19 | 15 of 44 |
| **Deferred by instruction** | 9–12 | Onboarding — to be scheduled |
| **Not started** | 20–44 | Credit analysis onwards |

The platform now takes a loan application and walks it through the full six-step
workflow, from origination to a closed disbursement. What happens *inside* the
later steps — credit analysis proper, CIB, screening, the sanctioning-limit
matrix, real core banking — is what remains.

---

## Phase detail

### Phase 1 — Foundation (Milestones 1–4) — complete

| # | Milestone | Status | Delivered |
| - | --------- | ------ | --------- |
| 1 | Repository, Docker, PostgreSQL, Flyway, Spring Boot, Next.js | Complete | Monorepo, compose stack, API skeleton, portal, 18 module schemas |
| 2 | Infrastructure | Complete | Folded into Milestone 1 |
| 3 | Database foundation | Complete | Folded into Milestone 1 |
| 4 | Backend foundation | Complete | Envelope, error handling, correlation ids, OpenAPI, health |

### Phase 2 — Identity and access (Milestones 5–8) — complete

| # | Milestone | Status | Delivered |
| - | --------- | ------ | --------- |
| 5 | Authentication | Complete | Three sign-in journeys, MFA, JWT issue and rotation, lock-out, login audit trail, portal sign-in |
| 6 | RBAC | Complete | 14 seeded roles, permission catalogue, permission-guarded endpoints |
| 7 | Organization | Complete | One configurable unit tree; branch, region and head-office scope resolution |
| 8 | Customer | Complete | Customer master with addresses and identification, read through organisational scope; 10 seeded customers |

### Phase 3 — Onboarding (Milestones 9–12) — deferred

| # | Milestone | Status |
| - | --------- | ------ |
| 9 | Document management | Deferred by instruction |
| 10 | KYC | Deferred by instruction |
| 11 | Account products | Deferred by instruction |
| 12 | Account opening | Deferred by instruction |

Deferred at the client's request, to be scheduled later. Nothing in Milestones
13–17 depends on them.

### Phase 4 — Product and decisioning (Milestones 13–17) — complete

| # | Milestone | Status | Delivered |
| - | --------- | ------ | --------- |
| 13 | Loan product configuration | Complete | `product` schema, catalogue API, product registration |
| 14 | Product versioning | Complete | Draft → activate → retire, one live version enforced by the database, live terms immutable |
| 15 | Rule engine | Complete | `rules` schema, attribute catalogue, 9 operators, AND/OR/NOT, every evaluation recorded |
| 16 | Eligibility engine | Complete | `POST /api/v1/eligibility/check`, scope-filtered, fully explained |
| 17 | Loan calculator | Complete | Amount engine (7 caps, binding factor reported) and pricing calculator (§19, §20) |

### Phase 5 — Application and workflow (Milestones 18–19) — complete

| # | Milestone | Status | Delivered |
| - | --------- | ------ | --------- |
| 18 | Loan application | Complete | `application` schema (9 tables), the loan file as a snapshot, purposes as configuration, queries with retained answers, append-only status history |
| 19 | Workflow engine | Complete | `workflow` schema, 32 states across six steps, 65 transitions, 488 role grants, `available-actions`, and no role name anywhere in the engine |

### Phases 6–10 (Milestones 20–44) — not started

Credit analysis, CIB, screening, approval matrix, conditional and group approval,
disbursement, CBS, repayment, DPD, NPL, collections, notifications, mobile apps,
the remaining portals, reporting, audit, performance, security hardening and
production deployment.

The workflow states for all of them already exist. What is missing is what
happens inside them, not where a file may go.

---

## What Milestones 13–17 delivered

### Database

| Migration | Schema | Tables |
| --------- | ------ | ------ |
| `V6` | `product` | `t_loan_product`, `t_loan_product_version`, `t_product_tenure`, `t_product_fee`, `t_product_risk_limit` |
| `V7` | `rules` | `t_rule_attribute`, `t_rule_group`, `t_rule`, `t_rule_evaluation`, `t_rule_evaluation_detail` |

Both verified from an empty database: V1–V7 apply cleanly and Hibernate's
`validate` accepts every entity against them.

### API

| Method | Path | Permission |
| ------ | ---- | ---------- |
| GET | `/api/v1/products` | `product.view` |
| GET | `/api/v1/products/{code}` | `product.view` |
| POST | `/api/v1/products` | `product.configure` |
| POST | `/api/v1/products/{code}/versions` | `product.configure` |
| PUT | `/api/v1/products/{code}/versions/{n}` | `product.configure` |
| POST | `/api/v1/products/{code}/versions/{n}/activate` | `product.configure` |
| POST | `/api/v1/products/{code}/versions/{n}/retire` | `product.configure` |
| POST | `/api/v1/eligibility/check` | `eligibility.check` |
| POST | `/api/v1/loan-calculator` | `product.view` (+ `product.price` for a negotiated rate) |
| GET | `/api/v1/rules/groups` | `rules.view` |
| GET | `/api/v1/rules/attributes` | `rules.view` |

### Portal

Four screens, connected to the real API through server-side proxies — the access
token stays in an httpOnly cookie the page cannot read.

- **Products** — the catalogue, with the live version's terms, fees and per-grade
  ceilings
- **Eligibility** — assess a customer, with every criterion and every limit shown
- **Calculator** — instalment, interest, fees, VAT, net disbursement and the full
  schedule
- **Overview** — rebuilt to separate what works from what is still to come

### Demonstration sign-in

The sign-in page offers the six seeded staff accounts as a row of role pills;
clicking one fills the form and you press Sign in yourself. The roster is chosen
to make the platform's own rules visible rather than to cover all fourteen
roles:

| Employee id | Role | Posted to | Sees |
| ----------- | ---- | --------- | ---- |
| `EMP-10001` | System Administrator | NRBC | 10 customers · 11 permissions |
| `EMP-10002` | Branch Manager | BR-101 | 3 customers · 5 permissions |
| `EMP-10003` | Field Officer | BR-102 | 3 customers · 5 permissions |
| `EMP-10004` | Relationship Manager | RG-DHKN | 6 customers · 5 permissions |
| `EMP-10005` | Credit Analyst | PPC-01 | 10 customers · 6 permissions |
| `EMP-10006` | Head of Credit Risk | DEP-CRM | 10 customers · 7 permissions |

Signing in as the branch manager and then the relationship manager is the
quickest demonstration of organisational scope: three customers become six.

Local profile only, and three guards hold it there — the accounts are seeded by a
profile-guarded runner rather than a migration, the endpoint that publishes the
passwords is registered only under that profile, and the runner refuses to seed
if the database already holds staff who are not on the roster.
`LocalDemoAccountsTest` pins the first two by asserting the annotation on every
class that knows a password.

The sign-in column was rebalanced around it: the theme switcher sits top right,
the form takes the height that is left, and the status line rests on the floor.
Verified at 1600×1000 and 1366×768 in all three themes — nothing scrolls at
either size.

### Tests

**187 unit tests, all passing.** New across these milestone groups:

| Class | Cases | Covers |
| ----- | ----- | ------ |
| `LoanCalculatorTest` | 28 | All three interest methods, rounding, schedule integrity, frequencies, inversion, refused inputs |
| `RuleEvaluatorTest` | 30 | Every operator against every data type, negation, misconfigurations |
| `RuleEngineTest` | 12 | AND/OR combination, reasons, empty and deactivated rules, audit recording |
| `LoanAmountEngineTest` | 13 | The specification's worked example, binding factors, unconfigured caps, rounding |
| `CustomerRuleContextTest` | 9 | Attribute completeness and absent-data handling |
| `LocalDemoAccountsTest` | 12 | The local-profile guard on every class knowing a demo password, and roster coverage of every workflow step |
| `WorkflowServiceTest` | 14 | Both gates, grants adding up across roles, role-specific transitions, and refusing to guess an ambiguous move |

Integration tests (4 classes, 14 cases) are kept current but **have never been
executed**: they need a Docker daemon, and this workstation cannot run one.

---

## What Milestones 18–19 delivered

### Database

| Migration | Schema | Tables |
| --------- | ------ | ------ |
| `V8` | `workflow` | `t_workflow_state`, `t_role_state_map`, `t_state_transition` |
| `V9` | `application` | `t_loan_purpose`, `t_loan_application`, `t_loan_application_applicant`, `t_loan_application_financial`, `t_loan_application_document`, `t_loan_application_status_history`, `t_loan_application_comment`, `t_loan_application_query`, `t_loan_application_query_response` |

Seeded: 32 workflow states across the six steps, 65 legal transitions, 488
role/state grants and six loan purposes. Checked on a clean database — no state
is unreachable, and no non-terminal state has no way out.

### The rule the milestone is really about

> Do NOT hard-code role names inside workflow business logic. For example, do
> not write `if (role.equals("BM"))`.

`WorkflowService` reads three tables and answers two questions: what may this
person do here, and is this particular move allowed. It contains no role name and
no state name. Adding a role, a state or a seventh step is an `INSERT`.

Two gates must agree before an action is offered — the role/state map has to
grant it, and the transition table has to offer a move for it. A grant with no
transition is a button that fails when pressed; a transition with no grant is a
move nobody can make.

### API

| Method | Path | Permission |
| ------ | ---- | ---------- |
| GET | `/api/v1/loan-applications` (`?state=`) | `application.view` |
| GET | `/api/v1/loan-applications/purposes` | `application.view` |
| GET | `/api/v1/loan-applications/{no}` | `application.view` |
| GET | `/api/v1/loan-applications/{no}/available-actions` | `application.view` |
| POST | `/api/v1/loan-applications` | `application.create` |
| POST | `/api/v1/loan-applications/{no}/actions` | `application.act` |
| POST | `/api/v1/loan-applications/{no}/comments` | `application.act` |
| GET | `/api/v1/workflow/{states,transitions,permissions}` | `workflow.view` |

### Portal

- **Applications** — the queue, filterable by any configured workflow state
- **Application detail** — the terms it was judged under, the applicant as
  declared, the finances behind the ratio, every query with its answers, and the
  full trail
- **Actions** — rendered from `available-actions`, so the portal draws what the
  backend permits rather than deciding for itself

### The demonstration roster grew to ten

The six accounts could not walk the workflow: with no sourcing officer, every
file stranded in the first state. `SO`, `MIS`, `UH` and `CAD` were added, and a
test now asserts the roster covers every step.

---

## Verified end to end

Against a database built from empty, with the backend and portal running:

| Check | Result |
| ----- | ------ |
| Flyway V1–V7 on a clean database | 7 migrations applied, 0 failures |
| Hibernate schema validation | Passes against all V6/V7 entities |
| e-Loan seeded | v1 ACTIVE · 5,000–50,000 · 3/6/9/12 months · 9% reducing |
| Quote 35,000 over 12 months | EMI 3,060.80 · interest 1,729.62 · fees 525.00 + VAT 52.50 · payable 37,307.12 · net 34,422.50 |
| Repayment schedule | Sums to the total payable; closing balance reaches 0.00 |
| Eligibility, all 10 seeded customers | 6 eligible, 4 declined with correct reasons |
| Amount sizing | Binding factor correctly reported (PRODUCT_MAX or RISK_GRADE) |
| Audit trail | Evaluation + 6 detail rows + JSON context snapshot persisted per check |
| Version lifecycle | Draft → amend → activate retires v1 → quote switches to v2 → retire leaves nothing on sale |
| Refusals | Second draft 409 · amend an active version 409 · out-of-range amount 422 · unoffered tenure 422 |
| Migrations V1–V9 | 9 applied on a clean database, Hibernate `validate` accepts every entity |
| Workflow graph | 32 states, 65 transitions, 488 grants; no unreachable state, no dead end |
| Six-step walk | One application from `SO_CREATED` to `CLOSED` across ten roles, with a query raised and answered and the amount cut from 35,000 to 30,000 |
| Workflow refusals | Wrong role 403 · illegal move 403 · closed file 409 · missing reason 400 · ambiguous destination 400 |
| Application guards | A purpose needing detail refused without it; a credit analyst refused `application.create` |
| Demo accounts | All six seeded, granted and posted on one fresh start; every published credential authenticates |
| Scope, end to end | BM sees BR-101 (3), FO sees BR-102 (3), RM sees both (6), head office sees all (10) |
| Negotiated rate | Honoured with `product.price`, refused without it |

---

## Three defects found and fixed during this milestone group

**The existing exposure limit blocked good borrowers.** It was computed as the
product's own maximum minus declared liabilities, which meant a customer earning
135,000 a month with an 850,000 mortgage was refused a 50,000 personal loan — the
best borrower on the book, declined for holding a mortgage. A product maximum
caps what *that product* lends, not what a borrower may owe altogether. Replaced
with a configured `max_total_exposure` ceiling, left unset for e-Loan;
affordability is the debt burden ratio's job.

**A missing district aborted the whole assessment.** Building the rule context
mapped an address to its district before selecting it, so a customer living
abroad with no district recorded threw a `NullPointerException` and took all six
criteria with them instead of failing the one rule that tests district. Fixed,
with a regression test.

**The local bootstraps ran in the wrong order.** `LocalAuthBootstrap` carried no
`@Order`, so it ran at lowest precedence — last — while the organisation
bootstrap that posts its accounts ran at 20. On a fresh database the postings
were therefore all skipped, and a second start quietly repaired them, so the
fault only ever showed on the first run of a new environment. Ordered first.

---

## Known gaps, tracked

1. Integration tests have never run — no Docker daemon on this workstation.
2. No credit scorecard yet (Milestone 18). The amount engine reads the customer's
   `risk_profile` as their grade; the scorecard will write the same grades, so
   nothing moves when it arrives.
3. No existing-instalment data, so the debt burden ratio treats the whole
   allowance as free. The explanation says so rather than implying a check that
   has not happened. Resolved when the loan book and CIB feed exist.
4. Rule configuration is read-only over the API. Writes wait for maker-checker in
   Milestone 21.
5. `rules.configure` is granted but guards nothing yet.
