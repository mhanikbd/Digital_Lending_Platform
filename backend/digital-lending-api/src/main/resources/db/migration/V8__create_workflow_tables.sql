-- ============================================================================
-- V8 : The workflow engine.
--
-- The specification is unusually direct about what this must not be:
--
--     Do NOT hard-code role names inside workflow business logic.
--     For example, do not write: if (role.equals("BM")) { ... }
--     Instead query configured role/state/transition permissions.
--
-- So the six-step workflow is three tables of rows, and the engine is a lookup.
-- A bank that inserts a seventh step, or lets a new role recommend from a state,
-- changes data. Nothing in Java knows that BM is a branch manager.
--
--   t_workflow_state              what states exist, and which step each belongs to
--   t_role_state_map              who may do what, in which state
--   t_state_transition            which moves are legal, and what each is called
--
-- The third is the specification's T_STATE_RECOMMEND_RETURN_MAP under a name
-- that survives the arrival of the fourth kind of move. Recommend and return are
-- two of six actions the seeds below already need; a table called
-- "recommend_return" holding an ESCALATE row would be a table nobody trusts.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- The states
-- ---------------------------------------------------------------------------
CREATE TABLE workflow.t_workflow_state (
    code           VARCHAR(40)  PRIMARY KEY,
    name           VARCHAR(120) NOT NULL,
    description    VARCHAR(255),

    -- Which of the six steps this belongs to. Held on the state rather than
    -- inferred from its name, because a queue screen groups by step and reading
    -- the prefix of a code to decide where a row goes is how CA_RETURNED ends up
    -- filed under credit analysis when it belongs to origination.
    step_no        SMALLINT     NOT NULL,
    step_name      VARCHAR(60)  NOT NULL,

    -- INITIAL states can begin an application; TERMINAL ones end it. Everything
    -- else is INTERMEDIATE. An application in a TERMINAL state accepts no
    -- action, which is enforced by there being no transition out of one.
    state_type     VARCHAR(20)  NOT NULL DEFAULT 'INTERMEDIATE',

    -- What the applicant is told their application is doing. Deliberately
    -- coarser than the internal state: a customer does not need to know whether
    -- their file is with the branch manager or the operations manager.
    customer_stage VARCHAR(40)  NOT NULL DEFAULT 'IN_PROGRESS',

    -- Hours before the state is considered overdue, for the queue screens and
    -- the ageing reports. Null means no service level applies.
    sla_hours      SMALLINT,

    display_order  SMALLINT     NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_state_type CHECK (state_type IN ('INITIAL', 'INTERMEDIATE', 'TERMINAL')),
    CONSTRAINT ck_state_step CHECK (step_no BETWEEN 1 AND 9),
    CONSTRAINT ck_state_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_state_customer_stage CHECK (customer_stage IN (
        'DRAFT', 'SUBMITTED', 'IN_PROGRESS', 'INFORMATION_REQUIRED',
        'APPROVED', 'DISBURSED', 'DECLINED', 'WITHDRAWN'))
);

CREATE INDEX ix_workflow_state_step ON workflow.t_workflow_state (step_no, display_order);

COMMENT ON TABLE workflow.t_workflow_state IS 'The states of the six-step workflow. Rows, not an enum in Java';

-- ---------------------------------------------------------------------------
-- Who may do what, where
-- ---------------------------------------------------------------------------
CREATE TABLE workflow.t_role_state_map (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),

    -- By code rather than by id, and with the foreign key, so a typo in a
    -- configuration screen is refused at the point of configuring rather than
    -- silently granting nobody anything at the point of deciding.
    role_code   VARCHAR(40) NOT NULL REFERENCES auth.t_role (code) ON DELETE CASCADE,
    state_code  VARCHAR(40) NOT NULL REFERENCES workflow.t_workflow_state (code) ON DELETE CASCADE,

    -- VIEW is separate from EDIT on purpose. A credit analyst reading a file
    -- that is sitting with the branch is an ordinary thing; editing it is not.
    action      VARCHAR(30) NOT NULL,

    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ux_role_state_action UNIQUE (role_code, state_code, action),
    CONSTRAINT ck_role_state_action CHECK (action IN (
        'VIEW', 'EDIT', 'SUBMIT', 'RECOMMEND', 'RETURN', 'REJECT',
        'ALLOCATE', 'QUERY', 'APPROVE', 'APPROVE_WITH_CONDITION', 'ESCALATE',
        'DISBURSE', 'CANCEL')),
    CONSTRAINT ck_role_state_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_role_state_state ON workflow.t_role_state_map (state_code);
CREATE INDEX ix_role_state_role ON workflow.t_role_state_map (role_code);

COMMENT ON TABLE workflow.t_role_state_map IS 'Authoritative for role/state permissions. The engine queries this and nothing else';

-- ---------------------------------------------------------------------------
-- Which moves are legal
-- ---------------------------------------------------------------------------
CREATE TABLE workflow.t_state_transition (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    from_state   VARCHAR(40)  NOT NULL REFERENCES workflow.t_workflow_state (code) ON DELETE CASCADE,
    to_state     VARCHAR(40)  NOT NULL REFERENCES workflow.t_workflow_state (code) ON DELETE CASCADE,

    -- The action that causes the move. Together with from_state this is what an
    -- available-actions call resolves, and what an attempted move is checked
    -- against.
    action       VARCHAR(30)  NOT NULL,

    -- Which role this move belongs to, when several roles can take the same
    -- action from the same state and land somewhere different. The branch
    -- recommendation is the case that forces it: a branch manager, an operations
    -- manager and the processing centre all RECOMMEND from SO_RECOMMENDED, and
    -- the file has to record which of them did it.
    --
    -- Null means the move is open to whoever the role/state map allows, which is
    -- almost all of them. Putting the answer here rather than in the engine is
    -- the whole point: adding a fourth branch role is a row.
    actor_role_code VARCHAR(40) REFERENCES auth.t_role (code) ON DELETE CASCADE,

    -- What the button says. Held here so the same word appears on the screen,
    -- in the history and in the notification, rather than three teams each
    -- choosing their own.
    label        VARCHAR(60)  NOT NULL,

    -- Whether the mover must say why. A return with no reason is a file that
    -- comes back to a branch with nothing to act on.
    reason_required BOOLEAN   NOT NULL DEFAULT FALSE,

    display_order SMALLINT    NOT NULL DEFAULT 100,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT ux_transition UNIQUE (from_state, action, to_state),
    CONSTRAINT ck_transition_action CHECK (action IN (
        'SUBMIT', 'RECOMMEND', 'RETURN', 'REJECT', 'ALLOCATE', 'QUERY',
        'APPROVE', 'APPROVE_WITH_CONDITION', 'ESCALATE', 'DISBURSE', 'CANCEL')),
    CONSTRAINT ck_transition_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    -- A state that moves to itself is a loop the queue never leaves.
    CONSTRAINT ck_transition_not_self CHECK (from_state <> to_state)
);

CREATE INDEX ix_transition_from ON workflow.t_state_transition (from_state);

COMMENT ON TABLE workflow.t_state_transition IS 'The legal moves. The specification calls this the recommend/return map; it carries every action, not two';

-- ---------------------------------------------------------------------------
-- Permissions introduced by this milestone
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_permission (code, name, description, module) VALUES
    ('workflow.view', 'View the workflow configuration',
     'Read the states, the role/state map and the legal transitions', 'WORKFLOW'),
    ('workflow.configure', 'Configure the workflow',
     'Change which role may act in which state, and which moves are legal', 'WORKFLOW');

-- Anybody who works an application needs to be able to see why an action is or
-- is not offered to them.
INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'workflow.view';

INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'workflow.configure' AND r.code = 'ADMIN';

-- ---------------------------------------------------------------------------
-- Seed: the six-step workflow, exactly as the specification sets it out.
--
-- Every state, transition and grant below is configuration. Changing who may
-- recommend from SO_CREATED is an UPDATE, not a release.
-- ---------------------------------------------------------------------------
INSERT INTO workflow.t_workflow_state
    (code, name, description, step_no, step_name, state_type, customer_stage, sla_hours, display_order)
VALUES
    -- Step 01: Origination
    ('FO_CREATED', 'Created by field officer',
     'A field officer is filling in an application on behalf of a customer', 1, 'Origination', 'INITIAL', 'DRAFT', 48, 10),
    ('FO_SUBMITTED', 'Submitted by field officer',
     'The field officer has submitted it to the branch', 1, 'Origination', 'INTERMEDIATE', 'SUBMITTED', 24, 20),
    ('SO_CREATED', 'With the sourcing officer',
     'Awaiting review by the sourcing officer. Returns land here', 1, 'Origination', 'INITIAL', 'IN_PROGRESS', 48, 30),
    ('SO_UPDATED', 'Updated by the sourcing officer',
     'The sourcing officer has amended the file. Editing is optional, so this state may be skipped', 1, 'Origination', 'INTERMEDIATE', 'IN_PROGRESS', 24, 40),
    ('SO_RECOMMENDED', 'Recommended by the sourcing officer',
     'Awaiting a branch recommendation', 1, 'Origination', 'INTERMEDIATE', 'IN_PROGRESS', 24, 50),

    -- Step 02: Branch approval. Exactly one of the three recommends.
    ('BM_RECOMMENDED', 'Recommended by the branch manager',
     'Recommended to head office by the branch manager', 2, 'Branch approval', 'INTERMEDIATE', 'IN_PROGRESS', 24, 60),
    ('BOM_RECOMMENDED', 'Recommended by branch operations',
     'Recommended to head office by the branch operations manager', 2, 'Branch approval', 'INTERMEDIATE', 'IN_PROGRESS', 24, 70),
    ('PPC_RECOMMENDED', 'Recommended by the processing centre',
     'Recommended to head office by the personal processing centre', 2, 'Branch approval', 'INTERMEDIATE', 'IN_PROGRESS', 24, 80),
    ('BM_RETURNED', 'Returned by the branch',
     'Sent back to the sourcing officer with a reason', 2, 'Branch approval', 'INTERMEDIATE', 'INFORMATION_REQUIRED', 24, 90),

    -- Step 03: Head office MIS
    ('MIS_RECEIVED', 'Received by head office',
     'Arrived at head office and awaiting allocation', 3, 'Head office', 'INTERMEDIATE', 'IN_PROGRESS', 24, 100),
    ('MIS_ALLOCATED', 'Allocated by head office',
     'Allocated to a credit analyst. External checks run alongside', 3, 'Head office', 'INTERMEDIATE', 'IN_PROGRESS', 24, 110),

    -- Step 04: Credit analysis
    ('CA_RECEIVED', 'With the credit analyst',
     'Awaiting credit analysis', 4, 'Credit analysis', 'INTERMEDIATE', 'IN_PROGRESS', 48, 120),
    ('CA_UPDATED', 'Updated by the credit analyst',
     'The analyst has recorded their assessment', 4, 'Credit analysis', 'INTERMEDIATE', 'IN_PROGRESS', 24, 130),
    ('CA_SEND_QUERY', 'Query raised by the credit analyst',
     'Awaiting an answer to a query. The question and the answer are both kept', 4, 'Credit analysis', 'INTERMEDIATE', 'INFORMATION_REQUIRED', 72, 140),
    ('CA_CONDITION_FULFILLED', 'Query answered',
     'The query has been answered and analysis may continue', 4, 'Credit analysis', 'INTERMEDIATE', 'IN_PROGRESS', 24, 150),
    ('CA_RECOMMENDED', 'Recommended by the credit analyst',
     'Awaiting a delegated approval decision', 4, 'Credit analysis', 'INTERMEDIATE', 'IN_PROGRESS', 24, 160),
    ('CA_RETURNED', 'Returned by the credit analyst',
     'Sent back to the sourcing officer with a reason', 4, 'Credit analysis', 'INTERMEDIATE', 'INFORMATION_REQUIRED', 24, 170),

    -- Step 05: Delegated approval. One state per tier, and escalation is a move
    -- between them, so adding a tier is a row rather than a branch in code.
    ('RM_REVIEW', 'With the relationship manager',
     'First delegated approval tier', 5, 'Approval', 'INTERMEDIATE', 'IN_PROGRESS', 24, 180),
    ('UH_REVIEW', 'With the unit head',
     'Second delegated approval tier', 5, 'Approval', 'INTERMEDIATE', 'IN_PROGRESS', 24, 190),
    ('HOCRM_REVIEW', 'With the head of credit risk',
     'Third delegated approval tier', 5, 'Approval', 'INTERMEDIATE', 'IN_PROGRESS', 24, 200),
    ('CEO_REVIEW', 'With the chief executive',
     'Fourth delegated approval tier', 5, 'Approval', 'INTERMEDIATE', 'IN_PROGRESS', 48, 210),
    ('MD_REVIEW', 'With the managing director',
     'Highest delegated approval tier', 5, 'Approval', 'INTERMEDIATE', 'IN_PROGRESS', 48, 220),
    ('APPROVED', 'Approved',
     'Approved and awaiting disbursement', 5, 'Approval', 'INTERMEDIATE', 'APPROVED', 24, 230),
    ('APPROVED_WITH_CONDITION', 'Approved with conditions',
     'Approved subject to conditions that must be met before disbursement', 5, 'Approval', 'INTERMEDIATE', 'APPROVED', 24, 240),

    -- Step 06: Disbursement
    ('CAD_DISBURSE', 'With credit administration',
     'Awaiting disbursement by credit administration', 6, 'Disbursement', 'INTERMEDIATE', 'APPROVED', 24, 250),
    ('SEND_TO_CBS', 'Sent to core banking',
     'A disbursement instruction has been sent to the core banking system', 6, 'Disbursement', 'INTERMEDIATE', 'APPROVED', 4, 260),
    ('CBS_SUCCESS', 'Core banking confirmed',
     'The core banking system has confirmed the disbursement', 6, 'Disbursement', 'INTERMEDIATE', 'DISBURSED', 4, 270),
    ('CBS_FAILED', 'Core banking rejected',
     'The core banking system refused or did not answer. Awaiting retry', 6, 'Disbursement', 'INTERMEDIATE', 'APPROVED', 4, 280),
    ('SMS_SENT', 'Customer notified',
     'The customer has been told the money is on its way', 6, 'Disbursement', 'INTERMEDIATE', 'DISBURSED', 4, 290),
    ('CLOSED', 'Closed',
     'The application is complete. The loan takes over from here', 6, 'Disbursement', 'TERMINAL', 'DISBURSED', NULL, 300),

    -- Outcomes that can end an application from several places at once. Given
    -- their own step so a queue screen does not have to file them under
    -- whichever step happened to produce them.
    ('REJECTED', 'Rejected',
     'Declined. The reason is on the status history', 9, 'Closed', 'TERMINAL', 'DECLINED', NULL, 900),
    ('CANCELLED', 'Cancelled',
     'Withdrawn before a decision was reached', 9, 'Closed', 'TERMINAL', 'WITHDRAWN', NULL, 910);

-- ---------------------------------------------------------------------------
-- Seed: the legal moves
-- ---------------------------------------------------------------------------
INSERT INTO workflow.t_state_transition (from_state, to_state, action, label, reason_required, display_order)
VALUES
    -- Step 01
    ('FO_CREATED', 'FO_SUBMITTED', 'SUBMIT', 'Submit to branch', FALSE, 10),
    ('FO_SUBMITTED', 'SO_CREATED', 'ALLOCATE', 'Take up at branch', FALSE, 10),
    ('SO_CREATED', 'SO_UPDATED', 'SUBMIT', 'Save changes', FALSE, 10),
    -- Editing is optional, so the file may go straight from SO_CREATED to
    -- recommended without passing through SO_UPDATED.
    ('SO_CREATED', 'SO_RECOMMENDED', 'RECOMMEND', 'Recommend', FALSE, 20),
    ('SO_UPDATED', 'SO_RECOMMENDED', 'RECOMMEND', 'Recommend', FALSE, 10),
    ('SO_CREATED', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),
    ('SO_UPDATED', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),
    ('SO_CREATED', 'CANCELLED', 'CANCEL', 'Cancel', TRUE, 95),
    ('FO_CREATED', 'CANCELLED', 'CANCEL', 'Cancel', TRUE, 95),

    -- Step 02: exactly one of the three branch recommendations
    ('SO_RECOMMENDED', 'BM_RETURNED', 'RETURN', 'Return to sourcing officer', TRUE, 80),
    ('SO_RECOMMENDED', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),
    ('BM_RETURNED', 'SO_CREATED', 'ALLOCATE', 'Take up again', FALSE, 10),

    ('BM_RECOMMENDED', 'MIS_RECEIVED', 'ALLOCATE', 'Receive at head office', FALSE, 10),
    ('BOM_RECOMMENDED', 'MIS_RECEIVED', 'ALLOCATE', 'Receive at head office', FALSE, 10),
    ('PPC_RECOMMENDED', 'MIS_RECEIVED', 'ALLOCATE', 'Receive at head office', FALSE, 10),

    -- Step 03
    ('MIS_RECEIVED', 'MIS_ALLOCATED', 'ALLOCATE', 'Allocate to credit', FALSE, 10),
    ('MIS_RECEIVED', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),
    ('MIS_ALLOCATED', 'CA_RECEIVED', 'ALLOCATE', 'Take up for analysis', FALSE, 10),

    -- Step 04
    ('CA_RECEIVED', 'CA_UPDATED', 'SUBMIT', 'Save analysis', FALSE, 10),
    ('CA_RECEIVED', 'CA_SEND_QUERY', 'QUERY', 'Raise a query', TRUE, 30),
    ('CA_UPDATED', 'CA_SEND_QUERY', 'QUERY', 'Raise a query', TRUE, 30),
    ('CA_SEND_QUERY', 'CA_CONDITION_FULFILLED', 'SUBMIT', 'Answer the query', TRUE, 10),
    ('CA_CONDITION_FULFILLED', 'CA_RECOMMENDED', 'RECOMMEND', 'Recommend for approval', FALSE, 10),
    ('CA_UPDATED', 'CA_RECOMMENDED', 'RECOMMEND', 'Recommend for approval', FALSE, 20),
    ('CA_RECEIVED', 'CA_RECOMMENDED', 'RECOMMEND', 'Recommend for approval', FALSE, 20),
    ('CA_RECEIVED', 'CA_RETURNED', 'RETURN', 'Return to branch', TRUE, 80),
    ('CA_UPDATED', 'CA_RETURNED', 'RETURN', 'Return to branch', TRUE, 80),
    ('CA_RECEIVED', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),
    ('CA_UPDATED', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),
    ('CA_RETURNED', 'SO_CREATED', 'ALLOCATE', 'Take up again', FALSE, 10),

    -- Step 05: approve, approve with condition, or escalate to the next tier
    ('CA_RECOMMENDED', 'RM_REVIEW', 'ALLOCATE', 'Take up for approval', FALSE, 10),

    ('RM_REVIEW', 'APPROVED', 'APPROVE', 'Approve', FALSE, 10),
    ('RM_REVIEW', 'APPROVED_WITH_CONDITION', 'APPROVE_WITH_CONDITION', 'Approve with conditions', TRUE, 20),
    ('RM_REVIEW', 'UH_REVIEW', 'ESCALATE', 'Escalate to unit head', TRUE, 30),
    ('RM_REVIEW', 'CA_RETURNED', 'RETURN', 'Return to credit', TRUE, 80),
    ('RM_REVIEW', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),

    ('UH_REVIEW', 'APPROVED', 'APPROVE', 'Approve', FALSE, 10),
    ('UH_REVIEW', 'APPROVED_WITH_CONDITION', 'APPROVE_WITH_CONDITION', 'Approve with conditions', TRUE, 20),
    ('UH_REVIEW', 'HOCRM_REVIEW', 'ESCALATE', 'Escalate to head of credit risk', TRUE, 30),
    ('UH_REVIEW', 'CA_RETURNED', 'RETURN', 'Return to credit', TRUE, 80),
    ('UH_REVIEW', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),

    ('HOCRM_REVIEW', 'APPROVED', 'APPROVE', 'Approve', FALSE, 10),
    ('HOCRM_REVIEW', 'APPROVED_WITH_CONDITION', 'APPROVE_WITH_CONDITION', 'Approve with conditions', TRUE, 20),
    ('HOCRM_REVIEW', 'CEO_REVIEW', 'ESCALATE', 'Escalate to the chief executive', TRUE, 30),
    ('HOCRM_REVIEW', 'CA_RETURNED', 'RETURN', 'Return to credit', TRUE, 80),
    ('HOCRM_REVIEW', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),

    ('CEO_REVIEW', 'APPROVED', 'APPROVE', 'Approve', FALSE, 10),
    ('CEO_REVIEW', 'APPROVED_WITH_CONDITION', 'APPROVE_WITH_CONDITION', 'Approve with conditions', TRUE, 20),
    ('CEO_REVIEW', 'MD_REVIEW', 'ESCALATE', 'Escalate to the managing director', TRUE, 30),
    ('CEO_REVIEW', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),

    ('MD_REVIEW', 'APPROVED', 'APPROVE', 'Approve', FALSE, 10),
    ('MD_REVIEW', 'APPROVED_WITH_CONDITION', 'APPROVE_WITH_CONDITION', 'Approve with conditions', TRUE, 20),
    ('MD_REVIEW', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),

    -- Step 06
    ('APPROVED', 'CAD_DISBURSE', 'ALLOCATE', 'Take up for disbursement', FALSE, 10),
    ('APPROVED_WITH_CONDITION', 'CAD_DISBURSE', 'ALLOCATE', 'Take up for disbursement', FALSE, 10),
    ('CAD_DISBURSE', 'SEND_TO_CBS', 'DISBURSE', 'Send to core banking', FALSE, 10),
    ('CAD_DISBURSE', 'REJECTED', 'REJECT', 'Reject', TRUE, 90),
    ('SEND_TO_CBS', 'CBS_SUCCESS', 'SUBMIT', 'Record core banking success', FALSE, 10),
    ('SEND_TO_CBS', 'CBS_FAILED', 'SUBMIT', 'Record core banking failure', TRUE, 20),
    ('CBS_FAILED', 'SEND_TO_CBS', 'DISBURSE', 'Retry core banking', FALSE, 10),
    ('CBS_SUCCESS', 'SMS_SENT', 'SUBMIT', 'Notify the customer', FALSE, 10),
    ('SMS_SENT', 'CLOSED', 'SUBMIT', 'Close the application', FALSE, 10);

-- The branch recommendation, one row per role that may give it. Same state,
-- same action, three destinations - which is exactly why the transition carries
-- the role rather than the engine deciding.
INSERT INTO workflow.t_state_transition
    (from_state, to_state, action, actor_role_code, label, reason_required, display_order)
VALUES
    ('SO_RECOMMENDED', 'BM_RECOMMENDED', 'RECOMMEND', 'BM', 'Recommend to head office', FALSE, 10),
    ('SO_RECOMMENDED', 'BOM_RECOMMENDED', 'RECOMMEND', 'BOM', 'Recommend to head office', FALSE, 20),
    ('SO_RECOMMENDED', 'PPC_RECOMMENDED', 'RECOMMEND', 'PPC', 'Recommend to head office', FALSE, 30);

-- ---------------------------------------------------------------------------
-- Seed: who may act where.
--
-- Read this as the org chart of the workflow. The administrator is not on it:
-- being able to configure the workflow is not the same as being able to walk an
-- application through it, and a bank that wants both grants both.
-- ---------------------------------------------------------------------------

-- Everybody who works applications can read one, wherever it happens to be
-- sitting. Explaining to a customer where their file is should not require
-- taking it off the person who has it.
INSERT INTO workflow.t_role_state_map (role_code, state_code, action)
SELECT r.code, s.code, 'VIEW'
FROM auth.t_role r
CROSS JOIN workflow.t_workflow_state s
WHERE r.code IN ('FO', 'SO', 'BM', 'BOM', 'PPC', 'MIS', 'CA', 'RM', 'UH', 'HOCRM', 'CEO', 'MD', 'CAD');

INSERT INTO workflow.t_role_state_map (role_code, state_code, action)
VALUES
    -- Step 01
    ('FO', 'FO_CREATED', 'EDIT'),
    ('FO', 'FO_CREATED', 'SUBMIT'),
    ('FO', 'FO_CREATED', 'CANCEL'),
    ('SO', 'FO_SUBMITTED', 'ALLOCATE'),
    ('SO', 'SO_CREATED', 'EDIT'),
    ('SO', 'SO_CREATED', 'SUBMIT'),
    ('SO', 'SO_CREATED', 'RECOMMEND'),
    ('SO', 'SO_CREATED', 'REJECT'),
    ('SO', 'SO_CREATED', 'CANCEL'),
    ('SO', 'SO_UPDATED', 'EDIT'),
    ('SO', 'SO_UPDATED', 'RECOMMEND'),
    ('SO', 'SO_UPDATED', 'REJECT'),
    ('SO', 'BM_RETURNED', 'ALLOCATE'),
    ('SO', 'CA_RETURNED', 'ALLOCATE'),

    -- Step 02: three roles may give the branch recommendation, and any of them
    -- may send it back instead.
    ('BM', 'SO_RECOMMENDED', 'RECOMMEND'),
    ('BM', 'SO_RECOMMENDED', 'RETURN'),
    ('BM', 'SO_RECOMMENDED', 'REJECT'),
    ('BOM', 'SO_RECOMMENDED', 'RECOMMEND'),
    ('BOM', 'SO_RECOMMENDED', 'RETURN'),
    ('PPC', 'SO_RECOMMENDED', 'RECOMMEND'),
    ('PPC', 'SO_RECOMMENDED', 'RETURN'),

    -- Step 03
    ('MIS', 'BM_RECOMMENDED', 'ALLOCATE'),
    ('MIS', 'BOM_RECOMMENDED', 'ALLOCATE'),
    ('MIS', 'PPC_RECOMMENDED', 'ALLOCATE'),
    ('MIS', 'MIS_RECEIVED', 'ALLOCATE'),
    ('MIS', 'MIS_RECEIVED', 'REJECT'),

    -- Step 04
    ('CA', 'MIS_ALLOCATED', 'ALLOCATE'),
    ('CA', 'CA_RECEIVED', 'EDIT'),
    ('CA', 'CA_RECEIVED', 'SUBMIT'),
    ('CA', 'CA_RECEIVED', 'QUERY'),
    ('CA', 'CA_RECEIVED', 'RECOMMEND'),
    ('CA', 'CA_RECEIVED', 'RETURN'),
    ('CA', 'CA_RECEIVED', 'REJECT'),
    ('CA', 'CA_UPDATED', 'EDIT'),
    ('CA', 'CA_UPDATED', 'QUERY'),
    ('CA', 'CA_UPDATED', 'RECOMMEND'),
    ('CA', 'CA_UPDATED', 'RETURN'),
    ('CA', 'CA_UPDATED', 'REJECT'),
    ('CA', 'CA_CONDITION_FULFILLED', 'RECOMMEND'),
    -- The branch answers the analyst's query, not the analyst.
    ('SO', 'CA_SEND_QUERY', 'SUBMIT'),
    ('SO', 'CA_SEND_QUERY', 'EDIT'),

    -- Step 05
    ('RM', 'CA_RECOMMENDED', 'ALLOCATE'),
    ('RM', 'RM_REVIEW', 'APPROVE'),
    ('RM', 'RM_REVIEW', 'APPROVE_WITH_CONDITION'),
    ('RM', 'RM_REVIEW', 'ESCALATE'),
    ('RM', 'RM_REVIEW', 'RETURN'),
    ('RM', 'RM_REVIEW', 'REJECT'),
    ('UH', 'UH_REVIEW', 'APPROVE'),
    ('UH', 'UH_REVIEW', 'APPROVE_WITH_CONDITION'),
    ('UH', 'UH_REVIEW', 'ESCALATE'),
    ('UH', 'UH_REVIEW', 'RETURN'),
    ('UH', 'UH_REVIEW', 'REJECT'),
    ('HOCRM', 'HOCRM_REVIEW', 'APPROVE'),
    ('HOCRM', 'HOCRM_REVIEW', 'APPROVE_WITH_CONDITION'),
    ('HOCRM', 'HOCRM_REVIEW', 'ESCALATE'),
    ('HOCRM', 'HOCRM_REVIEW', 'RETURN'),
    ('HOCRM', 'HOCRM_REVIEW', 'REJECT'),
    ('CEO', 'CEO_REVIEW', 'APPROVE'),
    ('CEO', 'CEO_REVIEW', 'APPROVE_WITH_CONDITION'),
    ('CEO', 'CEO_REVIEW', 'ESCALATE'),
    ('CEO', 'CEO_REVIEW', 'REJECT'),
    ('MD', 'MD_REVIEW', 'APPROVE'),
    ('MD', 'MD_REVIEW', 'APPROVE_WITH_CONDITION'),
    ('MD', 'MD_REVIEW', 'REJECT'),

    -- Step 06
    ('CAD', 'APPROVED', 'ALLOCATE'),
    ('CAD', 'APPROVED_WITH_CONDITION', 'ALLOCATE'),
    ('CAD', 'CAD_DISBURSE', 'DISBURSE'),
    ('CAD', 'CAD_DISBURSE', 'REJECT'),
    ('CAD', 'SEND_TO_CBS', 'SUBMIT'),
    ('CAD', 'CBS_FAILED', 'DISBURSE'),
    ('CAD', 'CBS_SUCCESS', 'SUBMIT'),
    ('CAD', 'SMS_SENT', 'SUBMIT');
