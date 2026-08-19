# Product configuration

> **Status: design intent. Implemented in Milestones 13 and 14.**

## 1. Why versioning comes first

A loan is judged under the rules in force when it was assessed, and it must stay
judged under them for its whole life. So a product is never edited in place: a
change creates a new version with its own effective dates and status.

Every application stores `product_id` **and** `product_version_id`. Re-opening a
three-year-old application must reproduce the exact configuration it was
evaluated against.

## 2. Product master

Product ID, code, name, name in Bangla, type, category, description, customer
segment, customer type, secured or unsecured, currency, minimum and maximum
amount, minimum and maximum tenure, interest or profit method, interest rate,
processing fee, VAT, insurance, other fees, grace period, repayment frequency,
collateral requirement, guarantor requirement, status, effective from, effective
to.

All monetary fields are `NUMERIC(20,4)`; rates are `NUMERIC(9,6)`.

## 3. Products to support

e-Loan (first), Quick Loan, Instant Loan, Personal Loan, Car Loan, Student Loan,
Home Loan, SME/CMSME Loan, Credit Card.

The test of the design: each of these after e-Loan should be introducible through
the admin portal, without new Java for product-specific rules.

## 4. e-Loan initial configuration

| Parameter | Value |
| --------- | ----- |
| Currency | BDT |
| Minimum amount | 5,000 |
| Maximum amount | 50,000 |
| Tenure | 3 to 12 months |

These are **initial configuration values, not business rules**. The admin portal
must be able to change every one of them through product configuration and
versioning. No number here may appear in Java source.
