# Approval engine

> **Status: design intent. Implemented in Milestones 23 to 25.**

## 1. Configurable authority

Approval authority is decided by configuration keyed on amount, product, risk
grade, customer segment, branch, business unit and further criteria.

An illustrative matrix:

| Amount | Tier |
| ------ | ---- |
| up to 50K | RM |
| above 50K to 500K | UH |
| above 500K to 5M | HOCRM |
| above 5M | CEO/MD |

**These are examples only and must not be hard-coded.** A bank changes its
delegation grid without a release.

## 2. Tables

`t_approval_matrix`, `t_approval_tier`, `t_approval_limit`, `t_approval_product`,
`t_approval_role`, `t_approval_delegation`, `t_approval_history`,
`t_approval_condition`.

## 3. Actions at each tier

Approve, Approve with condition, or Escalate.

## 4. Conditional approval

A condition is a first-class record, not free text on the approval. Example:
*submit updated salary certificate before disbursement*.

Conditions travel with the loan to CAD, remain visible throughout, block
disbursement until satisfied, and are auditable. CAD cannot disburse a loan whose
conditions are outstanding.

## 5. Group approval

Tables: `t_loan_group`, `t_loan_group_member`, `t_loan_group_action`,
`t_loan_group_approval`, `t_loan_group_history`.

Several individually appraised loans are grouped for a bulk decision:

```
Loan 1, Loan 2, Loan 3
  → CREATE_LOAN_GROUP
  → BULK_HOCRM_SEND_TO_MD    or    BULK_HOCRM_SEND_TO_CAD
```

The group is the decision unit, never a replacement for the individual loan
record. Each loan retains its own id, documents, comments, approval history,
audit history, repayment and account.
