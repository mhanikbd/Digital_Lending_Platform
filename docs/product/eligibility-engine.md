# Eligibility and loan amount engines

> **Status: design intent. Implemented in Milestones 15 to 17.**

## 1. Rule engine

Generic, not lending-specific. A rule is an attribute, an operator and a value;
rules combine into groups with priorities.

**Attributes**: age, income, occupation, KYC status, account status, account age,
account balance, CIB, existing loan, existing overdue, DBR, credit score, risk
grade, employment duration, transaction history, customer type, residence status,
geography, and further configurable attributes.

**Operators**: `=`, `!=`, `>`, `>=`, `<`, `<=`, `IN`, `NOT_IN`, `BETWEEN`.

**Combinators**: `AND`, `OR`, `NOT`.

Example:

```
Age >= 21 AND Age <= 60
  AND KYC = VERIFIED
  AND CIB = CLEAN
  AND Existing DPD = 0
  AND Monthly Income >= 20000
```

Every evaluation is persisted: the result, the individual rule results, the
product version used, the timestamp and the input snapshot. A decision that
cannot be reconstructed later is not auditable, and this is a regulated lending
decision.

## 2. Eligibility API

```
POST /api/v1/eligibility/check
```

```json
{
  "eligible": true,
  "productId": "ELOAN",
  "maxAmount": "50000.0000",
  "recommendedAmount": "35000.0000",
  "availableTenures": [3, 6, 9, 12],
  "interestRate": "9.000000",
  "riskGrade": "A"
}
```

Not one value in that response is hard-coded. All of it comes from product and
rule configuration.

## 3. Loan amount engine

There is no single maximum. The eligible amount is the tightest of several
constraints:

```
Final = MIN(
    product maximum,
    income based limit,
    credit score limit,
    existing exposure limit,
    regulatory limit,
    customer segment limit
)
```

The engine must record **which constraint bound the result**, not just the
number:

```
Product max            50,000
Income limit           40,000
Risk limit             35,000
Existing exposure      30,000   ← binding
Final eligible amount  30,000
```

An officer explaining a decision to a customer, and an auditor reviewing it,
both need that line.

## 4. Credit scorecard

Configurable weighted factors: account history, income, repayment history, CIB,
transaction behaviour, customer profile. Bands map a score to a grade, for
example 800-1000 to A, 700-799 to B, 600-699 to C, below 600 to reject. The
thresholds are configuration.

No machine learning in phase one. The interfaces are shaped so a model can be
introduced later behind the same scoring contract.
