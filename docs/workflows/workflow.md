# Loan workflow

> **Status: implemented in Milestones 18 and 19.**
> Schemas `workflow` and `application`, migrations `V8` and `V9`, API under
> `/api/v1/loan-applications` and `/api/v1/workflow`.

## 1. Principle

States, transitions and role permissions are **data**, not code. Three tables are
authoritative:

| Table | Answers |
| ----- | ------- |
| `workflow.t_workflow_state` | Which states exist, and which of the six steps each belongs to |
| `workflow.t_role_state_map` | Which role may take which action in which state |
| `workflow.t_state_transition` | Which moves are legal, what each is called, and whether a reason is required |

`WorkflowService` queries these and nothing else. Search it for `"BM"` and you
will not find it — which is the test the specification actually sets:

> Do NOT hard-code role names inside workflow business logic. For example, do not
> write `if (role.equals("BM")) { ... }`.

Adding a role, a state or a whole seventh step is an `INSERT`.

### On the third table's name

The specification calls it `T_STATE_RECOMMEND_RETURN_MAP`. It is
`t_state_transition` here because the seeds already need eleven actions —
`SUBMIT`, `RECOMMEND`, `RETURN`, `REJECT`, `ALLOCATE`, `QUERY`, `APPROVE`,
`APPROVE_WITH_CONDITION`, `ESCALATE`, `DISBURSE`, `CANCEL` — and a table called
"recommend_return" holding an `ESCALATE` row is a table nobody trusts.

## 2. The six steps, as seeded

32 states, 65 transitions, 488 role grants. Verified on a clean database: no
state is unreachable, and no non-terminal state has no way out.

| Step | States |
| ---- | ------ |
| 1 · Origination | `FO_CREATED` `FO_SUBMITTED` `SO_CREATED` `SO_UPDATED` `SO_RECOMMENDED` |
| 2 · Branch approval | `BM_RECOMMENDED` `BOM_RECOMMENDED` `PPC_RECOMMENDED` `BM_RETURNED` |
| 3 · Head office | `MIS_RECEIVED` `MIS_ALLOCATED` |
| 4 · Credit analysis | `CA_RECEIVED` `CA_UPDATED` `CA_SEND_QUERY` `CA_CONDITION_FULFILLED` `CA_RECOMMENDED` `CA_RETURNED` |
| 5 · Approval | `RM_REVIEW` `UH_REVIEW` `HOCRM_REVIEW` `CEO_REVIEW` `MD_REVIEW` `APPROVED` `APPROVED_WITH_CONDITION` |
| 6 · Disbursement | `CAD_DISBURSE` `SEND_TO_CBS` `CBS_SUCCESS` `CBS_FAILED` `SMS_SENT` `CLOSED` |
| 9 · Closed | `REJECTED` `CANCELLED` |

Rejection and cancellation get a step of their own because they can end an
application from several places at once, and a queue screen should not have to
file them under whichever step happened to produce them.

Editing by the sourcing officer is optional, as the specification requires:
`SO_CREATED` transitions directly to `SO_RECOMMENDED` as well as through
`SO_UPDATED`.

Escalation is a move between tier states rather than a field on the application,
so adding a tier is a row rather than a branch in code.

## 3. Two gates, and why both

An action is offered only when **both** agree:

- the role/state map grants it to one of the caller's roles, and
- the transition table offers a move for it from where the file sits.

A grant with no transition is a button that fails when pressed. A transition with
no grant is a move nobody can make. `VIEW` and `EDIT` are the exceptions — they
change nothing, so they need no transition.

Grants from several roles **add up**. Holding an extra role must never take an
action away.

## 4. Disambiguation

Two cases where a state and an action do not identify one destination, and each
is solved by data rather than by the engine guessing.

**By role.** Three roles `RECOMMEND` from `SO_RECOMMENDED` and land in three
different states. The transition carries `actor_role_code`, so a branch manager
sees exactly one move and the file records which of the three made it.

**By outcome.** `SEND_TO_CBS` can go to `CBS_SUCCESS` or `CBS_FAILED`, decided by
what core banking answered rather than by who is asking. Neither is tagged with a
role, so the engine refuses to guess and the caller must name the destination.
Guessing would close a disbursement that actually failed.

## 5. Available actions

```
GET /api/v1/loan-applications/{applicationNo}/available-actions
```

```json
[
  { "action": "RECOMMEND", "label": "Recommend to head office",
    "toState": "BM_RECOMMENDED", "reasonRequired": false },
  { "action": "RETURN", "label": "Return to sourcing officer",
    "toState": "BM_RETURNED", "reasonRequired": true }
]
```

The specification asks for this endpoint by name, and the reason is worth stating:
a screen that worked out for itself which buttons a branch manager should see
would be the same hard-coding forbidden in the backend, moved somewhere harder to
audit. The portal renders what it is told, including whether to show the reason
box.

The label comes from the configuration, so the word on the button, the word in
the history and the word in a later notification are the same word.

## 6. Taking an action

```
POST /api/v1/loan-applications/{applicationNo}/actions
```

```json
{ "action": "ESCALATE", "reason": "Above my sanctioning limit." }
```

Four checks, in this order:

1. the caller can see the file at all (organisational scope),
2. the file is not in a terminal state,
3. the workflow permits the action from where it sits,
4. a reason was given, when the transition demands one.

The order matters. Refusing for a missing reason before checking authority would
tell somebody which actions exist on a file they may not touch.

Refusals are distinguishable on purpose:

| Situation | Answer |
| --------- | ------ |
| Role does not hold the action here | `403 ACCESS_DENIED` — "Your role does not permit RECOMMEND while the application is SO_CREATED" |
| State offers no such move | `409 CONFLICT` — "RECOMMEND is not a move this application can make from CLOSED" |
| Application already finished | `409 CONFLICT` — "This application is Closed and accepts no further action" |
| Reason required and absent | `400 VALIDATION_FAILED` — "Reject requires a reason" |
| Two destinations, none named | `400 VALIDATION_FAILED` — "SUBMIT from SEND_TO_CBS can lead to CBS_SUCCESS or CBS_FAILED. Say which." |

## 7. The application itself

A loan application is a **snapshot**, not a set of pointers:

- the **product version** it was judged under, never the product;
- the **quotation** as computed when the file was raised — instalment, total
  payable, net disbursement;
- the **applicant** as declared, copied from the customer record;
- the **finances** the ratio was computed from, and the ratio itself.

A product repriced next month must not silently change the basis of a decision
already taken, and re-opening a three-year-old file has to reproduce what was
actually in front of the approver.

The approved amount is separate from the requested one and is never assumed
equal to it. An approver who cuts a loan from 50,000 to 30,000 has made a
decision, and it has to be visible as one.

### Queries

§23 requires that queries "retain original questions and responses in the audit
trail". The question is written once and never edited; each answer is a row
beneath it. A query answered twice keeps both, because the first answer is
usually what explains why there was a second.

### The trail

`t_loan_application_status_history` is append-only and made of **text, not
foreign keys** — the user who moved the file may later be deleted and the state
may later be retired. A history that can be emptied by a tidy-up is not a
history.

Both the user and the role are recorded, because a person may hold two roles and
"which hat were they wearing" is exactly the question an audit asks. Where the
transition names a role, that is the one recorded.

Unlike the rule engine's audit, this one is written **inside** the caller's
transaction: a rule evaluation is a read that must be recorded even when the
request fails, but a history row for a move that rolled back would describe
something that never happened.

## 8. Verified end to end

One application walked through all six steps on a clean database, signing in as
a different demonstration account at each stage:

```
raised APP-2026-000001                    SO_CREATED             step 1
SO     RECOMMEND    -> SO_RECOMMENDED     step 1
BM     RECOMMEND    -> BM_RECOMMENDED     step 2
MIS    ALLOCATE     -> MIS_RECEIVED       step 3
MIS    ALLOCATE     -> MIS_ALLOCATED      step 3
CA     ALLOCATE     -> CA_RECEIVED        step 4
CA     QUERY        -> CA_SEND_QUERY      step 4
SO     SUBMIT       -> CA_CONDITION_FULFILLED  step 4
CA     RECOMMEND    -> CA_RECOMMENDED     step 4
RM     ALLOCATE     -> RM_REVIEW          step 5
RM     ESCALATE     -> UH_REVIEW          step 5
UH     ESCALATE     -> HOCRM_REVIEW       step 5
HOCRM  APPROVE      -> APPROVED           step 5   (cut to 30,000)
CAD    ALLOCATE     -> CAD_DISBURSE       step 6
CAD    DISBURSE     -> SEND_TO_CBS        step 6
CAD    SUBMIT       -> CBS_SUCCESS        step 6
CAD    SUBMIT       -> SMS_SENT           step 6
CAD    SUBMIT       -> CLOSED             step 6
```

Seventeen history rows, each naming the actor and their role; one query with its
answer retained; and the approved amount recorded as 30,000 against a requested
35,000.

## 9. Not yet built

Milestones 20 onwards: the credit analysis workspace, CIB and screening
abstractions, the sanctioning-limit matrix that decides which approval tier a
file should start at, the core banking integration behind `SEND_TO_CBS`, and the
notification that `SMS_SENT` currently only records.

The states for all of them already exist. What is missing is what happens
*inside* them, not where a file may go.
