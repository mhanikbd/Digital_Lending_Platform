# Repayment engine

> **Status: design intent. Implemented in Milestones 28 and 35.**

## 1. Channels

Bank account, bKash, Nagad, Rocket, payment gateway, auto-debit. Each sits behind
`MfsProvider` or `PaymentProvider` with a mock for development.

## 2. Payment lifecycle

```
INITIATED → PROCESSING → SUCCESS | FAILED → RECONCILED
```

Required behaviour: retry, reversal, duplicate callback handling, idempotency and
receipt generation.

Duplicate callbacks are the norm, not the exception, with mobile financial
services. Every callback carries an idempotency key and is processed exactly
once; a repeat is acknowledged and discarded, never applied twice.

A timeout is not a failure. The payment may have succeeded, so the state stays
`PROCESSING` until reconciliation resolves it.

## 3. Allocation waterfall

Configurable per product. A common order:

```
Penalty → Fees → Interest → Principal
```

Other products may allocate differently. **No single waterfall is hard-coded
globally.** The order is configuration, and the allocation of every payment is
persisted line by line so an outstanding balance can always be explained.

## 4. Interest and pricing

Flat rate, reducing balance, effective rate, risk-grade based pricing,
tenure-based pricing, product-based pricing, promotional rates, processing fee,
VAT, insurance, late payment charge, penalty interest.

All arithmetic uses `BigDecimal` with an explicit rounding mode at each step. The
backend is authoritative; a client-side figure is indicative only.

## 5. Calculator

```
POST /api/v1/loan-calculator
```

In: product, amount, tenure, applicable rate.
Out: principal, interest, fees, VAT, insurance, total payable, EMI, net
disbursement.

Every calculation type and rounding rule needs a unit test. This is where a bug
becomes a regulatory finding rather than a defect.
