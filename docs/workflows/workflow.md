# Loan workflow

> **Status: design intent. Implemented in Milestone 19.**
> Nothing described here exists in code yet. It is recorded now so that the
> module boundaries and the `workflow` schema are shaped correctly.

## 1. Principle

States, transitions and role permissions are **data**, not code. Two tables are
authoritative:

| Table | Answers |
| ----- | ------- |
| `workflow.t_role_state_map` | Which role may act on which state |
| `workflow.t_state_recommend_return_map` | Which transitions are legal from a state |

Service code queries these. It never contains `if (role.equals("BM"))` and never
hard-codes a transition. Adding a role or reshaping the flow for a new product is
a configuration change.

Clients never infer available actions. They ask:

```
GET /api/v1/loan-applications/{id}/available-actions
```

## 2. Roles

FO (Field Officer), SO (Sourcing Officer), BM (Branch Manager), BOM (Branch
Operations Manager), PPC (Personal Processing Centre), MIS, CA (Credit Analyst),
RM (Relationship Manager), UH (Unit Head), HOCRM (Head of Credit Risk
Management), CEO, MD, CAD (Credit Administration Department).

All configurable in the authorisation system.

## 3. The six steps

### Step 1 — Origination

```
FO_CREATED → FO_SUBMITTED → SO_CREATED → SO_UPDATED → SO_RECOMMENDED
```

SO editing is optional, so `SO_CREATED` may go straight to `SO_RECOMMENDED`.

### Step 2 — Branch approval

Exactly one of `BM_RECOMMENDED`, `BOM_RECOMMENDED`, `PPC_RECOMMENDED`, then
`MIS_RECEIVED`. The return path is `BM_RETURNED → SO_CREATED`.

### Step 3 — Head office MIS

```
MIS_RECEIVED → MIS_ALLOCATED
```

MIS then dispatches parallel activities: `SENT_TO_CIB`, `MAILED_TO_POLICE`,
`SENT_TO_CAD`. NID verification and sanction screening run alongside. Because
these are concurrent and externally dependent, each is tracked as its own record
with its own state rather than as a position in the main flow.

### Step 4 — Credit analysis

```
CA_RECEIVED → CA_UPDATED → CA_SEND_QUERY → CA_CONDITION_FULFILLED → CA_RECOMMENDED
```

Return path: `CA_RETURNED → SO_CREATED`.

Queries retain the original question and every response in the audit trail. A
query is never overwritten by its answer.

### Step 5 — Delegated approval

```
RM → UH → HOCRM → CEO/MD
```

The tier is chosen by the configured sanctioning limits, never by a hard-coded
amount band. Each tier can Approve, Approve with condition, or Escalate.

Conditions travel with the loan to CAD and stay visible and auditable.

### Step 6 — Disbursement

```
CAD_DISBURSE → SEND_TO_CBS → CBS_SUCCESS → SMS_SENT → CLOSED
```

The CBS call needs a correlation id, a request id, an idempotency key, a retry
policy, a timeout and a reconciliation status. A timeout is not a failure: the
disbursement may have succeeded, so reconciliation decides, not a retry.

## 4. Group approval

Several individually appraised loans can be grouped so that HOCRM sends them to
MD or to CAD in bulk. The group is the decision unit; it does not replace the
individual loan record. Each loan keeps its own id, documents, comments, approval
history, audit history, repayment and account.

## 5. Audit

Every transition records who, role, what, when, IP, device, before and after
values, reason, application id, loan id, state before, state after and
correlation id.
