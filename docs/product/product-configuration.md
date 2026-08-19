# Product configuration

> **Status: implemented in Milestones 13 and 14.**
> Schema `product`, migration `V6`, API under `/api/v1/products`.

## 1. Why versioning comes first

A loan is judged under the terms in force when it was assessed, and it must stay
judged under them for its whole life. So a product is never edited in place: a
change creates a new version with its own effective dates and status.

That is not a convention this codebase asks you to remember. It is enforced:

- `t_loan_product` holds **nothing that gets repriced** — a code, a name, a type,
  a category. There is no rate column on it to edit by accident.
- `LoanProductVersion.amend(...)` refuses outright on any version that is not a
  draft, and the API turns that refusal into `409 CONFLICT`.
- A partial unique index permits **one** `ACTIVE` version per product, so
  "which terms apply" cannot become a question with two answers.

Every application will store `product_id` **and** `product_version_id`.
Re-opening a three-year-old application reproduces the exact configuration it
was evaluated against, because the retired version row is still there.

## 2. What lives where

| On the product | On the version |
| -------------- | -------------- |
| code, name, name in Bangla | amount bounds, tenures, rate and method |
| type, category, description | fees, VAT, risk ceilings |
| active or inactive | income multiple, debt burden ratio, regulatory and exposure ceilings |
|  | effective from and to, customer segment, currency, status |

All monetary fields are `NUMERIC(20,4)`; rates are `NUMERIC(9,6)` and are stored
as **percent per annum** — `9.000000` means nine percent — because that is what a
product sheet says. Ratios are stored as fractions: `max_dbr = 0.5000` is fifty
percent of income.

### Tables

| Table | Holds |
| ----- | ----- |
| `product.t_loan_product` | The product identity |
| `product.t_loan_product_version` | The terms, one row per version |
| `product.t_product_tenure` | The discrete tenures offered, which is not every month between the bounds |
| `product.t_product_fee` | Processing, insurance, penalties — with VAT and a collection point |
| `product.t_product_risk_limit` | The ceiling for each risk grade |

## 3. The version lifecycle

```
            create product          draft next version
                  │                        │
                  ▼                        ▼
              ┌───────┐  activate     ┌───────┐  activate    ┌─────────┐
              │ DRAFT │ ────────────► │ ACTIVE│ ───────────► │ RETIRED │
              └───────┘               └───────┘  (superseded)└─────────┘
                                          │  retire
                                          └────────────────► RETIRED
```

- **DRAFT** is editable and is not on sale. At most one per product.
- **ACTIVE** is being lent against and cannot be edited. At most one per product,
  enforced by the database.
- **RETIRED** stays forever. Loans are still being repaid under terms that are no
  longer sold, and this row is the only record of what they were.

Activating a draft retires the incumbent **in the same transaction**, so the
product is never briefly unsellable and the unique index is never violated.

A new draft is copied from the live version — fees, tenures and risk ceilings
included — so an amendment names only what actually changed. The copies are
genuine copies, not shared rows: editing version 2's processing fee cannot reach
back and change what version 1 charged the people still repaying under it.

## 4. Endpoints

| Method and path | Permission | Purpose |
| --------------- | ---------- | ------- |
| `GET /api/v1/products` | `product.view` | The catalogue, each product with the version on sale today in full |
| `GET /api/v1/products/{code}` | `product.view` | One product with every version it has ever had |
| `POST /api/v1/products` | `product.configure` | Register a product and draft its first version |
| `POST /api/v1/products/{code}/versions` | `product.configure` | Draft the next version, copied from the live one |
| `PUT /api/v1/products/{code}/versions/{n}` | `product.configure` | Amend a draft |
| `POST /api/v1/products/{code}/versions/{n}/activate` | `product.configure` | Put a draft on sale, retiring the incumbent |
| `POST /api/v1/products/{code}/versions/{n}/retire` | `product.configure` | Withdraw a version without replacing it |

`product.configure` is granted to `ADMIN` alone today. It stays there until the
maker and checker of Milestone 21 exist to divide it properly: a product version
decides what every subsequent application is judged by, and one click is not the
right amount of ceremony for that.

A product with no live version answers "not currently on sale" from both the
quotation and the eligibility endpoints, rather than falling back to older terms.

## 5. Products to support

e-Loan (first), Quick Loan, Instant Loan, Personal Loan, Car Loan, Student Loan,
Home Loan, SME/CMSME Loan, Credit Card.

The test of the design: each of these after e-Loan should be introducible through
`POST /api/v1/products` without new Java for product-specific rules. Nothing in
the product, rule, eligibility or pricing code branches on a product code.

## 6. e-Loan, as seeded

| Parameter | Value |
| --------- | ----- |
| Currency | BDT |
| Amount | 5,000 to 50,000 |
| Tenures | 3, 6, 9, 12 months |
| Interest | 9% per annum, reducing balance, monthly |
| Income multiple | 10× declared monthly income |
| Maximum debt burden | 50% of income |
| Regulatory ceiling | 50,000 |
| Total exposure ceiling | none |
| Offered share | 70% of the eligible maximum |
| Processing fee | 1% of principal, plus 15% VAT, at disbursement |
| Insurance | 0.5% of principal, at disbursement |
| Late payment | 500 flat, on default |
| Risk ceilings | LOW 50,000 · MEDIUM 35,000 · HIGH 15,000 |

These are **initial configuration values, not business rules**. Every one of them
is changed by issuing version 2, not by editing a migration and certainly not by
editing Java. No number in that table appears in Java source.
