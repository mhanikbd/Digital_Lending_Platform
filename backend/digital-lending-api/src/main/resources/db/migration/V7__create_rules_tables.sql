-- ============================================================================
-- V7 : The rule engine.
--
-- The specification is explicit that eligibility criteria are configuration.
-- "Age >= 21 AND Age <= 60 AND KYC = VERIFIED AND CIB = CLEAN AND DPD = 0 AND
-- income >= 20000" must be something a bank edits, not something a developer
-- deploys. So a rule is a row: an attribute, an operator, and a value.
--
-- Four tables, and each earns its place.
--
--   t_rule_attribute   what may be tested. A catalogue, so a screen can offer a
--                      list instead of a free-text box, and a typo in an
--                      attribute name fails at configuration time rather than
--                      silently evaluating to false at decision time.
--   t_rule_group       how rules combine, with a priority and one message for
--                      the whole group.
--   t_rule             the test itself.
--   t_rule_evaluation  what happened, kept forever, because a customer who was
--   + _detail          declined is entitled to know which rule declined them and
--                      the bank is required to be able to say.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- What can be tested
-- ---------------------------------------------------------------------------
CREATE TABLE rules.t_rule_attribute (
    code         VARCHAR(60)  PRIMARY KEY,
    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(255),

    -- Decides which operators are legal and how the stored value is parsed.
    data_type    VARCHAR(20)  NOT NULL,

    -- Where the value comes from when the context is assembled. Recorded so an
    -- administrator can see why an attribute is unavailable for a given subject.
    source       VARCHAR(40)  NOT NULL,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_attribute_type CHECK (data_type IN ('NUMBER', 'STRING', 'BOOLEAN', 'DATE'))
);

COMMENT ON TABLE rules.t_rule_attribute IS 'The catalogue of testable facts. A rule may only name a code that exists here';

-- ---------------------------------------------------------------------------
-- How rules combine
-- ---------------------------------------------------------------------------
CREATE TABLE rules.t_rule_group (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),

    code                VARCHAR(40)   NOT NULL,
    name                VARCHAR(120)  NOT NULL,
    description         VARCHAR(255),

    -- Null means the group applies to every product. A group tied to a version
    -- is evaluated only for that version, which is how repricing can also change
    -- who qualifies.
    product_version_id  UUID          REFERENCES product.t_loan_product_version (id) ON DELETE CASCADE,

    purpose             VARCHAR(30)   NOT NULL DEFAULT 'ELIGIBILITY',

    -- AND means every rule must pass; OR means one is enough.
    logical_operator    VARCHAR(5)    NOT NULL DEFAULT 'AND',

    -- Lower runs first. Groups are ordered so the cheapest and most decisive
    -- checks can be put in front of the expensive ones.
    priority            SMALLINT      NOT NULL DEFAULT 100,

    -- What the customer is told when this group fails. Written for a person, and
    -- deliberately not assembled from the rules, which are written for a machine.
    failure_message     VARCHAR(255),

    status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',

    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by          VARCHAR(64)   NOT NULL DEFAULT 'system',
    version             BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ux_rule_group_code UNIQUE (code),
    CONSTRAINT ck_group_operator CHECK (logical_operator IN ('AND', 'OR')),
    CONSTRAINT ck_group_purpose CHECK (purpose IN ('ELIGIBILITY', 'CREDIT', 'SCREENING')),
    CONSTRAINT ck_group_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_rule_group_version ON rules.t_rule_group (product_version_id);

-- ---------------------------------------------------------------------------
-- The rule
-- ---------------------------------------------------------------------------
CREATE TABLE rules.t_rule (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id         UUID          NOT NULL REFERENCES rules.t_rule_group (id) ON DELETE CASCADE,

    attribute_code   VARCHAR(60)   NOT NULL REFERENCES rules.t_rule_attribute (code),
    operator         VARCHAR(10)   NOT NULL,

    -- Held as text and parsed against the attribute's declared type. A typed
    -- column per data type would be four mostly-null columns; a JSON blob would
    -- be unqueryable. For IN and NOT_IN this is a comma separated list; for
    -- BETWEEN it is the lower bound and comparison_value2 the upper.
    comparison_value   VARCHAR(255) NOT NULL,
    comparison_value2  VARCHAR(255),

    -- The NOT of the specification's AND/OR/NOT, applied to this rule alone.
    negate           BOOLEAN       NOT NULL DEFAULT FALSE,

    priority         SMALLINT      NOT NULL DEFAULT 100,
    failure_message  VARCHAR(255),
    status           VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',

    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version          BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ck_rule_operator CHECK (operator IN (
        'EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'BETWEEN')),
    CONSTRAINT ck_rule_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    -- BETWEEN is the only operator that needs a second value, and it always
    -- needs one. Catching that here beats discovering it mid-decision.
    CONSTRAINT ck_rule_between CHECK (
        (operator = 'BETWEEN' AND comparison_value2 IS NOT NULL)
        OR (operator <> 'BETWEEN' AND comparison_value2 IS NULL))
);

CREATE INDEX ix_rule_group ON rules.t_rule (group_id);

-- ---------------------------------------------------------------------------
-- What happened, kept
-- ---------------------------------------------------------------------------
CREATE TABLE rules.t_rule_evaluation (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),

    subject_type        VARCHAR(20)   NOT NULL DEFAULT 'CUSTOMER',
    subject_id          UUID          NOT NULL,

    -- The exact version evaluated. Without this the record is worthless a
    -- repricing later, because nobody can reconstruct what the rules were.
    product_version_id  UUID          REFERENCES product.t_loan_product_version (id) ON DELETE SET NULL,

    outcome             VARCHAR(20)   NOT NULL,

    -- The attribute values the decision was made on, exactly as they were at the
    -- time. The customer's income changes; the reason they were declined must
    -- not change with it.
    context_snapshot    JSONB         NOT NULL,

    correlation_id      VARCHAR(64),
    evaluated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_evaluation_outcome CHECK (outcome IN ('PASS', 'FAIL'))
);

CREATE INDEX ix_evaluation_subject ON rules.t_rule_evaluation (subject_id, evaluated_at DESC);
CREATE INDEX ix_evaluation_time ON rules.t_rule_evaluation (evaluated_at DESC);

COMMENT ON TABLE rules.t_rule_evaluation IS 'Append only. A declined customer is entitled to know why, years later';

CREATE TABLE rules.t_rule_evaluation_detail (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evaluation_id   UUID         NOT NULL REFERENCES rules.t_rule_evaluation (id) ON DELETE CASCADE,

    -- The codes rather than foreign keys: a rule may be edited or deleted after
    -- the decision, and the record of the decision must survive that intact.
    group_code      VARCHAR(40)  NOT NULL,
    attribute_code  VARCHAR(60)  NOT NULL,
    operator        VARCHAR(10)  NOT NULL,
    expected_value  VARCHAR(255) NOT NULL,
    actual_value    VARCHAR(255),

    passed          BOOLEAN      NOT NULL,
    message         VARCHAR(255)
);

CREATE INDEX ix_evaluation_detail_evaluation ON rules.t_rule_evaluation_detail (evaluation_id);

-- ---------------------------------------------------------------------------
-- Permissions introduced by this milestone
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_permission (code, name, description, module) VALUES
    ('rules.view', 'View rule configuration',
     'Read the rule groups, rules and attribute catalogue', 'RULES'),
    ('rules.configure', 'Configure rules',
     'Create and amend the rules that decide eligibility', 'RULES');

-- Anybody who has to explain a decline needs to be able to read the rules that
-- produced it, which is credit as much as it is administration.
INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'rules.view' AND r.code IN ('ADMIN', 'CA', 'HOCRM', 'CEO', 'MD', 'PPC');

INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'rules.configure' AND r.code = 'ADMIN';

-- ---------------------------------------------------------------------------
-- Seed: the attribute catalogue.
--
-- Only attributes the platform can actually supply a value for today. The
-- specification also lists CIB, credit score, account age and transaction
-- history; those arrive with the modules that produce them, and seeding them now
-- would let an administrator build a rule that silently never matches.
-- ---------------------------------------------------------------------------
INSERT INTO rules.t_rule_attribute (code, name, description, data_type, source) VALUES
    ('customer.age', 'Age in years', 'Derived from the date of birth on the day of evaluation', 'NUMBER', 'CUSTOMER'),
    ('customer.monthly_income', 'Total monthly income', 'Primary plus other declared income', 'NUMBER', 'CUSTOMER'),
    ('customer.existing_liabilities', 'Existing liabilities', 'Declared outstanding borrowing', 'NUMBER', 'CUSTOMER'),
    ('customer.net_worth', 'Net worth', 'Declared net worth', 'NUMBER', 'CUSTOMER'),
    ('customer.kyc_status', 'KYC status', 'PENDING, IN_PROGRESS, VERIFIED or REJECTED', 'STRING', 'CUSTOMER'),
    ('customer.risk_profile', 'Risk profile', 'LOW, MEDIUM or HIGH', 'STRING', 'CUSTOMER'),
    ('customer.type', 'Customer type', 'INDIVIDUAL, JOINT, MINOR and the rest', 'STRING', 'CUSTOMER'),
    ('customer.status', 'Customer status', 'ACTIVE, DORMANT or CLOSED', 'STRING', 'CUSTOMER'),
    ('customer.residence_status', 'Residence status', 'RESIDENT or NON_RESIDENT', 'STRING', 'CUSTOMER'),
    ('customer.occupation', 'Occupation', 'Declared occupation category', 'STRING', 'CUSTOMER'),
    ('customer.district', 'District', 'District of the present address', 'STRING', 'CUSTOMER'),
    ('customer.has_verified_nid', 'NID verified', 'Whether a verified national ID is held', 'BOOLEAN', 'CUSTOMER');

-- ---------------------------------------------------------------------------
-- Seed: the eligibility rules for e-Loan version 1.
--
-- This is the specification's own worked example, expressed as configuration.
-- Changing any of it is an update here, not a release.
-- ---------------------------------------------------------------------------
INSERT INTO rules.t_rule_group (
    code, name, description, product_version_id, purpose, logical_operator, priority, failure_message)
SELECT 'ELOAN_V1_BASE', 'e-Loan basic eligibility',
       'Age, identity and standing checks every e-Loan applicant must pass',
       v.id, 'ELIGIBILITY', 'AND', 10,
       'You do not currently meet the basic eligibility criteria for this product.'
FROM product.t_loan_product_version v
JOIN product.t_loan_product p ON p.id = v.product_id
WHERE p.code = 'ELOAN' AND v.version_no = 1;

INSERT INTO rules.t_rule (
    group_id, attribute_code, operator, comparison_value, comparison_value2, priority, failure_message)
SELECT g.id, r.attribute, r.operator, r.value1, r.value2, r.priority, r.message
FROM rules.t_rule_group g
CROSS JOIN (VALUES
    ('customer.age', 'BETWEEN', '21', '60', 10::SMALLINT,
     'Applicants must be between 21 and 60 years old.'),
    ('customer.kyc_status', 'EQ', 'VERIFIED', NULL, 20::SMALLINT,
     'Your identity verification must be complete before you can apply.'),
    ('customer.status', 'EQ', 'ACTIVE', NULL, 30::SMALLINT,
     'This account is not currently active.'),
    ('customer.type', 'IN', 'INDIVIDUAL,SOLE_PROPRIETOR', NULL, 40::SMALLINT,
     'This product is available to individual and sole proprietor customers.'),
    ('customer.monthly_income', 'GTE', '20000', NULL, 50::SMALLINT,
     'A minimum monthly income of BDT 20,000 is required.'),
    ('customer.residence_status', 'EQ', 'RESIDENT', NULL, 60::SMALLINT,
     'This product is available to residents of Bangladesh.')
) AS r(attribute, operator, value1, value2, priority, message)
WHERE g.code = 'ELOAN_V1_BASE';
