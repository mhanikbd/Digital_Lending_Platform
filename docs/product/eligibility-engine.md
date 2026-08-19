# Eligibility and loan amount engines

> **Status: implemented in Milestones 15 to 17.**
> Schema `rules`, migration `V7`, API under `/api/v1/eligibility` and
> `/api/v1/rules`.

## 1. Rule engine

Generic, not lending-specific. Nothing in `com.naztech.lending.rules` knows what
a loan is: it reads groups from the database, asks about each rule, combines the
answers the way the group says to, and files the result. Adding a criterion is an
`INSERT`.

A rule is an attribute, an operator and a value.

**Attributes** are a catalogue, not free text. `rules.t_rule_attribute` declares
what may be tested, what type it is, and which module supplies it. A rule may
only name a code that exists there, so a misspelt attribute is refused when the
rule is saved rather than silently evaluating to false when somebody's
application is being decided.

Twelve are seeded — the ones the platform can actually supply a value for today:

```
customer.age                    customer.kyc_status
customer.monthly_income         customer.risk_profile
customer.existing_liabilities   customer.type
customer.net_worth              customer.status
customer.occupation             customer.residence_status
customer.district               customer.has_verified_nid
```

The specification also lists CIB, credit score, account age and transaction
history. Those arrive with the modules that produce them. Seeding them now would
let an administrator build a rule that never matches and never says why.

**Operators**: `EQ`, `NEQ`, `GT`, `GTE`, `LT`, `LTE`, `IN`, `NOT_IN`, `BETWEEN`.
Which are legal depends on the attribute's type — a `STRING` accepts only
equality and set membership, because asking whether one KYC status is greater
than another is not a question.

**Combinators**: `AND` and `OR` on the group; `NOT` on an individual rule, via
its `negate` flag.

The seeded e-Loan group is the specification's own worked example, as data:

```
customer.age              BETWEEN 21 AND 60
customer.kyc_status       EQ      VERIFIED
customer.status           EQ      ACTIVE
customer.type             IN      INDIVIDUAL, SOLE_PROPRIETOR
customer.monthly_income   GTE     20000
customer.residence_status EQ      RESIDENT
```

### Behaviour worth knowing

- **Every rule is evaluated**, even once the group has already failed. Short
  circuiting would be faster and would leave a banker unable to answer "what else
  is wrong" without a fresh check after each fix.
- **A misconfiguration declines, it does not throw.** An attribute nobody could
  supply, a comparison value that will not parse, an operator the type does not
  accept — each fails that one rule with a recorded reason. An exception would
  abandon the assessment and take the other five criteria with it.
- **Absent is not the same as null.** Absent means no module could answer; null
  means the customer has not told us. Both decline, and they read differently in
  the audit record.
- **An empty group is satisfied**, not failed. Otherwise creating a group before
  filling it in would decline everybody.
- **Numbers compare by value and text without regard to case.** `20000` equals
  `20000.00`, and `VERIFIED` equals `Verified`. A capital letter is not grounds to
  decline someone.

### The audit record

Every evaluation is persisted, pass or fail: the outcome, each rule's result, the
product version used, the timestamp, the correlation id, and a JSON snapshot of
the attribute values the decision was made on.

Two details make it hold up:

- The detail rows carry **codes, not foreign keys**. A rule may be edited or
  deleted after the decision; a reason that changes when somebody retunes the
  criteria is not a reason at all.
- The record is written in its **own transaction** (`REQUIRES_NEW`). An audit row
  written inside the caller's transaction disappears when the caller rolls back,
  and a decline that leaves no trace is the one case where the record matters
  most.

## 2. Eligibility API

```
POST /api/v1/eligibility/check      → permission: eligibility.check
```

```json
{ "customerId": "CIF-000001", "productCode": "ELOAN" }
```

No amount, no rate, no tenure in the request. This endpoint answers what the
customer qualifies for, not whether a figure somebody already chose is
acceptable.

```json
{
  "eligible": true,
  "customerId": "CIF-000001",
  "productCode": "ELOAN",
  "productVersion": 1,
  "currency": "BDT",
  "maxAmount": "50000.00",
  "recommendedAmount": "35000.00",
  "availableTenures": [3, 6, 9, 12],
  "interestRate": "9.000000",
  "riskGrade": "LOW",
  "reasons": [],
  "criteria": [ … every rule and its result … ],
  "limits":   { … every cap and why … },
  "evaluationId": "fa85e5ad-…"
}
```

Not one value in that response is hard-coded. All of it comes from product and
rule configuration.

Two gates apply, as everywhere else in the platform: `eligibility.check` decides
who may run an assessment, and the caller's organisational scope decides whose
customers they may run it on. A customer outside that scope answers `404`,
exactly as one who does not exist.

A declined customer is **not sized**. Computing a limit for somebody who has just
been declined produces a figure that reads like an offer, and somebody will
eventually show it to them.

### Reading the rule configuration

```
GET /api/v1/rules/groups        → permission: rules.view
GET /api/v1/rules/attributes    → permission: rules.view
```

Read only. Editing rules through an API needs the maker and checker of
Milestone 21 — shipping the write endpoint first would mean shipping a way to
change lending policy with a single click.

## 3. Loan amount engine

There is no single maximum. The eligible amount is the tightest of every
configured cap:

```
Final = MIN(
    product maximum,
    income based limit,
    debt burden limit,
    credit risk limit,
    existing exposure limit,
    regulatory limit,
    customer segment limit
)
```

Six of those are the specification's; the seventh — the debt burden — is implied
by it and matters. An income multiple asks whether the customer earns enough. The
debt burden ratio asks whether the **instalment** fits inside what they earn.
They are different questions with different answers at long tenures, and a bank
that only asks the first lends more than it meant to. The engine converts the
ratio into a limit on the amount by running the amortisation backwards, at the
longest tenure the product offers.

Every factor is reported whether it bound or not, with the arithmetic in words:

```
PRODUCT_MAX        50,000.00           e-Loan version 1 lends up to 50,000.00
INCOME_MULTIPLE    40,000.00           10 times a declared monthly income of 4,000.00
DEBT_BURDEN               —            This product does not cap lending by debt burden ratio
RISK_GRADE         35,000.00           Risk grade MEDIUM may borrow up to 35,000.00
EXISTING_EXPOSURE  30,000.00  binding  Declared borrowing of 20,000.00 against a total ceiling of 50,000.00
REGULATORY                —            No regulatory ceiling is configured for this product
CUSTOMER_SEGMENT          —            This product version is open to every segment
```

A factor that is not configured is reported as **not configured**, not as a cap
of zero, and not omitted. A grade with no configured ceiling is not a grade
limited to nothing — reading silence as refusal would decline everybody the first
time a bank introduced a new grade. And a factor that silently vanishes from the
list is indistinguishable from one that was forgotten.

### Two decisions worth stating

**Existing exposure needs a configured ceiling.** It is a concentration control,
and the product's own maximum will not serve as the ceiling: that is what this
product lends, not what the borrower may owe altogether. Using it would refuse a
small personal loan to anybody holding a mortgage they are comfortably servicing
— which is to say, to the best borrowers on the book. So `max_total_exposure` is
a nullable column, unset for e-Loan, and affordability is left to the debt burden
ratio where it belongs.

**Every cap rounds down** to whole currency, always. A cap that rounds up is not
a cap.

### The recommended amount

The maximum is what the customer *could* borrow; the recommended amount is what
the bank *offers*. A customer lent their absolute ceiling has no headroom left
and defaults more often, so banks rarely lead with it. The share is
`recommended_ratio` on the version — seventy percent for e-Loan — because it is
marketing policy and a bank that disagrees should change a row.

It is never below the product minimum: an offer the product cannot honour is not
an offer. When the maximum itself falls below the minimum there is no loan at
all, `belowMinimum` is set, and the customer is declined with that as the reason.

## 4. Credit scorecard

**Not yet built — Milestone 18.** Configurable weighted factors: account history,
income, repayment history, CIB, transaction behaviour, customer profile. Bands map
a score to a grade; the thresholds are configuration.

Until it exists, the amount engine reads the customer's `risk_profile`
(`LOW`, `MEDIUM`, `HIGH`) as their grade. The scorecard will write the same
grades, so `t_product_risk_limit` and the engine's signature do not move when it
arrives — only who computes the grade does.

No machine learning in phase one. The interfaces are shaped so a model can be
introduced later behind the same scoring contract.
