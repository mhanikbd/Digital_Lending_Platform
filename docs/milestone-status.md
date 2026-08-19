# Milestone progress status

**As at 19 August 2026** · Digital Lending Platform for NRBC Bank · built by naztech

Updated on every commit. The specification lists 44 milestones across ten phases;
this records exactly where each one stands and, where something is deliberately
incomplete, why.

---

## Summary

| | Milestones | Status |
| --- | --- | --- |
| **Complete** | 1–8, 13–17 | 13 of 44 |
| **Deferred by instruction** | 9–12 | Onboarding — to be scheduled |
| **Not started** | 18–44 | Applications onwards |

The platform can now answer the two questions that precede a loan application:
**may this customer borrow**, and **on what terms**. What it cannot yet do is
accept the application.

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

### Phases 5–10 (Milestones 18–44) — not started

Loan applications, workflow, credit analysis, CIB, screening, approval,
disbursement, loans, repayment, collections, NPL, notifications, mobile apps,
portals, reporting, audit, performance, security hardening, deployment.

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

### Tests

**162 unit tests, all passing.** New in this milestone group:

| Class | Cases | Covers |
| ----- | ----- | ------ |
| `LoanCalculatorTest` | 28 | All three interest methods, rounding, schedule integrity, frequencies, inversion, refused inputs |
| `RuleEvaluatorTest` | 30 | Every operator against every data type, negation, misconfigurations |
| `RuleEngineTest` | 12 | AND/OR combination, reasons, empty and deactivated rules, audit recording |
| `LoanAmountEngineTest` | 13 | The specification's worked example, binding factors, unconfigured caps, rounding |
| `CustomerRuleContextTest` | 9 | Attribute completeness and absent-data handling |

Integration tests (4 classes, 14 cases) are kept current but **have never been
executed**: they need a Docker daemon, and this workstation cannot run one.

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
| Negotiated rate | Honoured with `product.price`, refused without it |

---

## Two defects found and fixed during this milestone group

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
