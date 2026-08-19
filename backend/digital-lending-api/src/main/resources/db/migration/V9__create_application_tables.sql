-- ============================================================================
-- V9 : Loan applications.
--
-- The file itself: who applied, for what, on whose terms, and everything that
-- has happened to it since.
--
-- Two ideas run through the whole schema.
--
-- The first is that an application is a **snapshot**, not a set of pointers. It
-- records the applicant's income as declared on the day, the product version it
-- was judged under, and the instalment it was quoted - because a customer whose
-- salary later changes must not silently change the basis of a decision already
-- taken, and re-opening a three-year-old file has to reproduce what was actually
-- in front of the approver.
--
-- The second is that nothing is ever overwritten. Status history, comments and
-- queries are append-only. The specification is explicit that queries "must
-- retain original questions and responses in the audit trail", and the same
-- reasoning applies to every other trace the file leaves.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Loan purposes: configuration, not an enum
-- ---------------------------------------------------------------------------
CREATE TABLE application.t_loan_purpose (
    code          VARCHAR(30)  PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    name_bn       VARCHAR(160),
    description   VARCHAR(255),

    -- Some purposes need evidence. A medical loan may require a hospital
    -- estimate; a personal one requires nothing. Held here so a bank can change
    -- its mind without a release.
    requires_detail BOOLEAN    NOT NULL DEFAULT FALSE,

    display_order SMALLINT     NOT NULL DEFAULT 100,
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_purpose_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

COMMENT ON TABLE application.t_loan_purpose IS 'The purposes a customer may choose. Rows, so a bank adds one without a release';

-- ---------------------------------------------------------------------------
-- The application
-- ---------------------------------------------------------------------------
CREATE TABLE application.t_loan_application (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),

    -- What a branch quotes on the phone. Generated, unique, and printed on the
    -- sanction letter.
    application_no       VARCHAR(30)   NOT NULL,

    customer_id          UUID          NOT NULL REFERENCES customer.t_customer (id) ON DELETE RESTRICT,

    -- Both, deliberately. The product says what was applied for; the version
    -- says on what terms, and it is the version that must still be readable
    -- when the product has been repriced four times.
    product_id           UUID          NOT NULL REFERENCES product.t_loan_product (id) ON DELETE RESTRICT,
    product_version_id   UUID          NOT NULL REFERENCES product.t_loan_product_version (id) ON DELETE RESTRICT,

    -- Where the file is now, and the whole of its authority to move.
    state_code           VARCHAR(40)   NOT NULL REFERENCES workflow.t_workflow_state (code) ON DELETE RESTRICT,

    -- Which branch owns it, so the same organisational scope that filters the
    -- customer list filters the queues.
    branch_id            UUID          REFERENCES organization.t_org_unit (id) ON DELETE RESTRICT,

    -- How it arrived. A field officer application carries the officer as well,
    -- which §22 requires and which the check below enforces rather than trusts.
    source_channel       VARCHAR(30)   NOT NULL,
    field_officer_id     UUID          REFERENCES auth.t_user (id) ON DELETE RESTRICT,

    -- What was asked for.
    requested_amount     NUMERIC(20,4) NOT NULL,
    requested_tenure_months SMALLINT   NOT NULL,
    purpose_code         VARCHAR(30)   NOT NULL REFERENCES application.t_loan_purpose (code) ON DELETE RESTRICT,
    purpose_detail       VARCHAR(500),

    -- What was approved, once somebody has approved something. Null until then,
    -- and never assumed equal to the requested amount: an approver who cuts a
    -- loan from 50,000 to 30,000 has made a decision that must be visible.
    approved_amount      NUMERIC(20,4),
    approved_tenure_months SMALLINT,

    -- The quotation as it stood when the application was submitted. Recomputing
    -- it later would silently reprice a file somebody has already signed.
    interest_rate        NUMERIC(9,6)  NOT NULL,
    interest_method      VARCHAR(30)   NOT NULL,
    instalment_amount    NUMERIC(20,4),
    total_payable        NUMERIC(20,4),
    net_disbursement     NUMERIC(20,4),

    -- Where the money goes. The account module has not arrived, so this is the
    -- number as given, not a foreign key to an account this platform holds.
    disbursement_account VARCHAR(34),

    -- The eligibility run that let this application exist. Nullable because a
    -- file created before the check completed is a file with no decision behind
    -- it, not an invalid one.
    eligibility_id       UUID,

    -- Consent, as §21 requires it: recorded, timed, and attributable.
    consent_given        BOOLEAN       NOT NULL DEFAULT FALSE,
    consent_at           TIMESTAMPTZ,
    consent_ip           VARCHAR(45),

    submitted_at         TIMESTAMPTZ,
    decided_at           TIMESTAMPTZ,

    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by           VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by           VARCHAR(64)   NOT NULL DEFAULT 'system',
    version              BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ux_application_no UNIQUE (application_no),
    CONSTRAINT ck_application_channel CHECK (source_channel IN (
        'MOBILE_APP', 'WEBSITE', 'BRANCH', 'FIELD_OFFICER', 'CALL_CENTRE')),
    -- §22: a field officer application records the officer. Enforced, because
    -- "every application must record field_officer_id" is not a convention.
    CONSTRAINT ck_application_field_officer CHECK (
        source_channel <> 'FIELD_OFFICER' OR field_officer_id IS NOT NULL),
    CONSTRAINT ck_application_amount CHECK (requested_amount > 0),
    CONSTRAINT ck_application_tenure CHECK (requested_tenure_months > 0),
    CONSTRAINT ck_application_approved CHECK (
        approved_amount IS NULL OR approved_amount > 0),
    CONSTRAINT ck_application_rate CHECK (interest_rate >= 0),
    -- Consent without a timestamp is consent nobody can prove was given.
    CONSTRAINT ck_application_consent CHECK (
        consent_given = FALSE OR consent_at IS NOT NULL)
);

CREATE INDEX ix_application_customer ON application.t_loan_application (customer_id);
CREATE INDEX ix_application_state ON application.t_loan_application (state_code);
CREATE INDEX ix_application_branch ON application.t_loan_application (branch_id);
CREATE INDEX ix_application_created ON application.t_loan_application (created_at DESC);

COMMENT ON TABLE application.t_loan_application IS 'The loan file. Records the terms it was judged under, not a pointer to whatever they are now';

-- ---------------------------------------------------------------------------
-- The applicant, as they were on the day
-- ---------------------------------------------------------------------------
CREATE TABLE application.t_loan_application_applicant (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id    UUID          NOT NULL REFERENCES application.t_loan_application (id) ON DELETE CASCADE,

    -- PRIMARY today. Joint applicants and guarantors get their own rows when
    -- the products that need them arrive, which is why this is a table and not
    -- a set of columns on the application.
    applicant_type    VARCHAR(20)   NOT NULL DEFAULT 'PRIMARY',

    full_name         VARCHAR(200)  NOT NULL,
    date_of_birth     DATE,
    gender            VARCHAR(20),
    mobile            VARCHAR(20)   NOT NULL,
    email             VARCHAR(160),
    national_id       VARCHAR(30),

    occupation        VARCHAR(60),
    employer_name     VARCHAR(160),
    designation       VARCHAR(120),

    present_address   VARCHAR(400),
    permanent_address VARCHAR(400),

    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_applicant_type CHECK (applicant_type IN ('PRIMARY', 'JOINT', 'GUARANTOR'))
);

CREATE INDEX ix_applicant_application ON application.t_loan_application_applicant (application_id);

COMMENT ON TABLE application.t_loan_application_applicant IS 'The applicant as declared on the day. A copy, not a join: the customer record moves on and the decision must not';

-- ---------------------------------------------------------------------------
-- The financial picture the decision was taken on
-- ---------------------------------------------------------------------------
CREATE TABLE application.t_loan_application_financial (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id        UUID          NOT NULL REFERENCES application.t_loan_application (id) ON DELETE CASCADE,

    monthly_income        NUMERIC(20,4) NOT NULL DEFAULT 0,
    other_monthly_income  NUMERIC(20,4) NOT NULL DEFAULT 0,
    monthly_expense       NUMERIC(20,4) NOT NULL DEFAULT 0,

    -- What they already owe, and what it costs them each month. The second is
    -- what the debt burden ratio actually needs; the first is what a customer
    -- can usually tell you.
    existing_liabilities  NUMERIC(20,4) NOT NULL DEFAULT 0,
    existing_emi          NUMERIC(20,4) NOT NULL DEFAULT 0,

    net_worth             NUMERIC(20,4),
    source_of_income      VARCHAR(120),
    source_of_funds       VARCHAR(120),

    -- Computed at submission and kept, so the ratio on the file is the ratio the
    -- approver saw rather than one recalculated from figures that have moved.
    debt_burden_ratio     NUMERIC(9,6),

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ux_financial_application UNIQUE (application_id),
    CONSTRAINT ck_financial_amounts CHECK (
        monthly_income >= 0 AND other_monthly_income >= 0 AND monthly_expense >= 0
        AND existing_liabilities >= 0 AND existing_emi >= 0)
);

COMMENT ON TABLE application.t_loan_application_financial IS 'Income and obligations as declared. One row per application, kept for the life of the loan';

-- ---------------------------------------------------------------------------
-- Documents attached to the file
-- ---------------------------------------------------------------------------
CREATE TABLE application.t_loan_application_document (
    id                UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id    UUID          NOT NULL REFERENCES application.t_loan_application (id) ON DELETE CASCADE,

    document_type     VARCHAR(40)   NOT NULL,
    file_name         VARCHAR(255)  NOT NULL,

    -- Where the bytes are. Object storage holds the file; this table holds the
    -- fact that the file belongs to this application. The document module of
    -- Milestone 9 will own the object itself, and this column is what it will
    -- point at.
    storage_key       VARCHAR(500)  NOT NULL,
    content_type      VARCHAR(120),
    size_bytes        BIGINT,

    verified          BOOLEAN       NOT NULL DEFAULT FALSE,
    verified_by       VARCHAR(64),
    verified_at       TIMESTAMPTZ,

    uploaded_by       VARCHAR(64)   NOT NULL DEFAULT 'system',
    uploaded_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_document_size CHECK (size_bytes IS NULL OR size_bytes > 0),
    CONSTRAINT ck_document_verified CHECK (verified = FALSE OR verified_at IS NOT NULL)
);

CREATE INDEX ix_document_application ON application.t_loan_application_document (application_id);

-- ---------------------------------------------------------------------------
-- Where it has been
-- ---------------------------------------------------------------------------
CREATE TABLE application.t_loan_application_status_history (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id   UUID         NOT NULL REFERENCES application.t_loan_application (id) ON DELETE CASCADE,

    -- Null on the first row: an application does not come from anywhere.
    from_state       VARCHAR(40),
    to_state         VARCHAR(40)  NOT NULL,
    action           VARCHAR(30)  NOT NULL,

    -- Who moved it, and under which role. Both, because a person may hold two
    -- roles and "which hat were they wearing" is exactly the question an audit
    -- asks. Stored as text so the record survives the user being deleted.
    actor_user_id    UUID,
    actor_username   VARCHAR(64)  NOT NULL,
    actor_role       VARCHAR(40),

    -- Why. Required by the transition for returns, rejections and escalations,
    -- and a return with no reason is a file that arrives back at a branch with
    -- nothing to act on.
    reason           VARCHAR(1000),

    correlation_id   VARCHAR(64),
    occurred_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_status_history_application
    ON application.t_loan_application_status_history (application_id, occurred_at);

COMMENT ON TABLE application.t_loan_application_status_history IS 'Append only. Every move the file has made, who made it and why';

-- ---------------------------------------------------------------------------
-- What people said about it
-- ---------------------------------------------------------------------------
CREATE TABLE application.t_loan_application_comment (
    id               BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id   UUID          NOT NULL REFERENCES application.t_loan_application (id) ON DELETE CASCADE,

    -- The state the file was in when the comment was made. Without it a remark
    -- reads as if it were made about the file as it is now.
    state_code       VARCHAR(40),

    author_user_id   UUID,
    author_username  VARCHAR(64)   NOT NULL,
    author_role      VARCHAR(40),

    comment          VARCHAR(2000) NOT NULL,

    -- An internal note is not shown to the customer. A bank needs somewhere to
    -- write "third application this quarter" that is not a letter.
    internal_only    BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_comment_application ON application.t_loan_application_comment (application_id, created_at);

-- ---------------------------------------------------------------------------
-- Queries, and their answers
--
-- §23: "Queries must retain original questions and responses in the audit
-- trail." So the question is a row that is never edited, and each answer is
-- another row beneath it. Editing a question after it has been answered would
-- leave an answer to something nobody asked.
-- ---------------------------------------------------------------------------
CREATE TABLE application.t_loan_application_query (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   UUID          NOT NULL REFERENCES application.t_loan_application (id) ON DELETE CASCADE,

    query_no         SMALLINT      NOT NULL,
    question         VARCHAR(2000) NOT NULL,

    -- What kind of thing is being asked for, so a screen can offer an upload
    -- rather than a text box when the answer is a document.
    query_type       VARCHAR(30)   NOT NULL DEFAULT 'INFORMATION',

    raised_by_user_id UUID,
    raised_by         VARCHAR(64)  NOT NULL,
    raised_by_role    VARCHAR(40),
    raised_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    status           VARCHAR(20)   NOT NULL DEFAULT 'OPEN',
    closed_at        TIMESTAMPTZ,

    CONSTRAINT ux_query_no UNIQUE (application_id, query_no),
    CONSTRAINT ck_query_type CHECK (query_type IN ('INFORMATION', 'DOCUMENT', 'CLARIFICATION')),
    CONSTRAINT ck_query_status CHECK (status IN ('OPEN', 'ANSWERED', 'CLOSED')),
    CONSTRAINT ck_query_closed CHECK (status <> 'CLOSED' OR closed_at IS NOT NULL)
);

CREATE INDEX ix_query_application ON application.t_loan_application_query (application_id, query_no);

CREATE TABLE application.t_loan_application_query_response (
    id                BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    query_id          UUID          NOT NULL REFERENCES application.t_loan_application_query (id) ON DELETE CASCADE,

    response          VARCHAR(2000) NOT NULL,

    -- A response may be a document rather than a sentence.
    document_id       UUID          REFERENCES application.t_loan_application_document (id) ON DELETE SET NULL,

    responded_by_user_id UUID,
    responded_by      VARCHAR(64)   NOT NULL,
    responded_by_role VARCHAR(40),
    responded_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX ix_query_response_query ON application.t_loan_application_query_response (query_id, responded_at);

COMMENT ON TABLE application.t_loan_application_query_response IS 'Append only, one row per answer. A query answered twice keeps both';

-- ---------------------------------------------------------------------------
-- Permissions introduced by this milestone
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_permission (code, name, description, module) VALUES
    ('application.view', 'View loan applications',
     'Read applications within the caller''s organisational scope', 'APPLICATION'),
    ('application.create', 'Create loan applications',
     'Start an application on behalf of a customer', 'APPLICATION'),
    ('application.act', 'Act on loan applications',
     'Take the workflow actions the role/state map allows', 'APPLICATION');

-- Reading applications is what the back office is for.
INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code IN ('application.view', 'application.act')
  AND r.code IN ('ADMIN', 'FO', 'SO', 'BM', 'BOM', 'PPC', 'MIS', 'CA',
                 'RM', 'UH', 'HOCRM', 'CEO', 'MD', 'CAD');

-- Origination is not. A credit analyst who can raise the file they are about to
-- assess is a control failure, so creating is granted to the roles that source.
INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'application.create' AND r.code IN ('ADMIN', 'FO', 'SO');

-- ---------------------------------------------------------------------------
-- Seed: the purposes §21 names
-- ---------------------------------------------------------------------------
INSERT INTO application.t_loan_purpose (code, name, name_bn, requires_detail, display_order) VALUES
    ('EMERGENCY', 'Emergency', 'জরুরি প্রয়োজন', FALSE, 10),
    ('EDUCATION', 'Education', 'শিক্ষা', TRUE, 20),
    ('BUSINESS', 'Business', 'ব্যবসা', TRUE, 30),
    ('MEDICAL', 'Medical', 'চিকিৎসা', TRUE, 40),
    ('TRAVEL', 'Travel', 'ভ্রমণ', FALSE, 50),
    ('PERSONAL', 'Personal', 'ব্যক্তিগত', FALSE, 60);
