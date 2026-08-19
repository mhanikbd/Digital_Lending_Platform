# Pricing and the loan calculator

> **Status: implemented in Milestones 16 and 17** (specification §19 and §20).
> Package `com.naztech.lending.pricing`, API at `/api/v1/loan-calculator`.
> No schema: pricing is stateless and reads product configuration.

## 1. The division of labour

Two classes, and keeping them apart is what makes the arithmetic testable.

**`LoanCalculator`** is pure. Numbers in, numbers out — no Spring, no database,
no clock, no product entity. Everything that could make the same inputs give a
different answer on a different day is an argument. That is what lets §20's
requirement of "unit tests for all calculation types and rounding rules" actually
be unit tests.

**`PricingService`** knows where the rate comes from, which fees apply and what
the product will accept. It looks up the live version, checks the amount and
tenure against it, and hands numbers to the calculator.

Neither knows what the other does, so the arithmetic can be tested exhaustively
without a database and the configuration can change without touching a formula.

## 2. Interest methods

| Method | Interest is charged on | For the same headline rate |
| ------ | ---------------------- | -------------------------- |
| `FLAT` | The original principal, for the whole term | Roughly twice the cost |
| `REDUCING_BALANCE` | Whatever is still owed | The market convention |
| `EFFECTIVE` | Whatever is still owed, at a rate that compounds to the stated annual figure | Slightly less than nominal |

The method is configuration precisely because those differ so much. 50,000 over
twelve months at 9%:

| | Flat | Reducing | Effective |
| --- | --- | --- | --- |
| Instalment | 4,541.67 | 4,372.57 | 4,364.43 |
| Total interest | 4,500.00 | 2,470.90 | 2,373.22 |

A nominal rate is divided by the number of periods in a year, which is the
convention every loan agreement in the market is written on. An effective rate is
un-compounded by taking the appropriate root — the one step done in double
precision, because there is no exact decimal *n*-th root. It produces a *rate*,
not money; it is immediately fixed at ten decimal places, and every figure
computed from it is `BigDecimal` throughout.

## 3. Rounding

Two scales, and the distinction is the whole of it.

- **Working values** carry ten decimal places, so compounding does not accumulate
  error.
- **Anything a customer is shown, pays or owes** is rounded to two, half up.

Rounding happens once at the end of each figure, never repeatedly through the
calculation.

### The last instalment

A rounded instalment multiplied by the number of instalments almost never equals
the exact total. 50,000 at 9% reducing over twelve months gives a level
instalment of 4,372.57 and a final one of 4,372.63 — the six paisa go on the last
row rather than being spread or quietly dropped.

That is not tidiness. The schedule must sum to exactly the total payable and the
balance must reach exactly zero, or the loan carries a residue that never clears
— and it becomes a collections problem long before anybody notices it was a
rounding problem. The property is asserted for every offered tenure, for both
flat and reducing, against an amount chosen because it divides evenly into
nothing.

The schedule is also built by **walking the balance down** rather than by
formula, because the balance is what the loan actually owes and the formula is
only a prediction of it.

## 4. Fees

Configured per version, never in code. Each fee knows how to compute itself, so
there is no `switch` on the calculation method repeated in every caller.

- `FLAT` — a fixed amount, whatever the loan is for.
- `PERCENT_OF_PRINCIPAL` — a share of the amount borrowed.

VAT is charged **on the fee**, not on the loan, and its rate is stored rather
than assumed: fifteen percent is the Bangladeshi figure today, not a constant of
nature. Base and VAT are reported separately because they are separate in law,
and a customer disputing a charge is entitled to see which part is which.

The collection point decides where a fee lands:

| Point | Effect |
| ----- | ------ |
| `DISBURSEMENT` | Deducted before the money reaches the customer |
| `EMI` | Added to the instalment |
| `ON_DEFAULT` | A penalty — **excluded from every quotation** |

That last exclusion is deliberate. Quoting a late payment charge to somebody who
has not yet borrowed states a cost they will only pay if they default, which is
not a cost of the loan.

## 5. The calculator API

```
POST /api/v1/loan-calculator      → permission: product.view
```

```json
{ "productCode": "ELOAN", "amount": "35000", "tenureMonths": 12 }
```

The rate is **not** in the request. A client may show an indicative figure while
a customer drags a slider, but what they are held to is what this endpoint
returned — and it cannot be authoritative if the client chooses the inputs to it.

For 35,000 over twelve months on e-Loan version 1:

| Figure | Value |
| ------ | ----- |
| Instalment | 3,060.80 |
| Total interest | 1,729.62 |
| Fees | 525.00 (processing 350.00 + insurance 175.00) |
| VAT on fees | 52.50 |
| Total payable | 37,307.12 |
| **Reaches the account** | **34,422.50** |

The last two are the figures customers most often get wrong. A processing fee
taken at disbursement means the amount borrowed and the amount received are
different numbers, and saying so plainly is cheaper than a complaint later.

The response also carries the full repayment schedule: principal, interest and
closing balance for every instalment.

### Negotiated rates

`rateOverride` quotes at a rate other than the published one. It requires
`product.price`, granted to `ADMIN`, `HOCRM`, `CEO` and `MD` — departing from the
published rate is a concession, and a concession is a credit decision. A rate
override from anyone else is **refused**, not ignored: silently quoting a
different rate from the one asked for is worse than saying no.

The quote carries `rateNegotiated: true`, so the figure can never be mistaken for
the standard one.

### Refusals

An amount or tenure the product does not offer is a `422` naming the bounds that
were applied, not a silent adjustment:

```
"e-Loan is available between 5000.0000 and 50000.0000"
"e-Loan is offered over 3, 6, 9, 12 months"
```

## 6. Inversion

`principalAffordableAt` runs the amortisation backwards: given a ceiling on the
instalment, the largest principal that stays within it. This is what lets the
debt burden ratio be expressed as a limit on the *amount* rather than only as a
test on a proposed one, and it is what the loan amount engine calls.

It rounds **down** to whole currency. Rounding up would produce an instalment a
paisa over the ceiling the bank has just set, which is the one direction that is
not allowed.

## 7. Tests

`LoanCalculatorTest` — 28 cases across five groups:

- **Flat**: interest on the whole principal for the whole term; halving the term
  halves the interest; every instalment carries the same interest.
- **Reducing**: matches the annuity formula worked independently at thirty
  significant figures; costs less than the same headline rate charged flat;
  interest falls and principal rises across the schedule; a zero rate is
  straight-line repayment rather than a division by zero.
- **Effective**: un-compounds rather than divides; costs slightly less than the
  same nominal rate.
- **Rounding**: the schedule sums to the total payable and clears the balance —
  parameterised over every offered tenure, for both methods; the difference lands
  on the last instalment; every figure is quoted to two places.
- **Frequencies and refusals**: quarterly repayment; a tenure that is not a whole
  number of periods; a zero principal; a negative rate.
