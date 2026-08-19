-- ============================================================================
-- V5 : The customer master.
--
-- Three tables, because the specification asks for three shapes of fact.
--
--   t_customer                who they are, what they earn, how risky they are
--   t_customer_address        where they are, and there is always more than one
--   t_customer_identification what they proved it with, each with its own dates
--
-- The identification documents get their own table rather than a dozen nullable
-- columns on the master. A passport has an issue date, an expiry, a place of
-- issue and a number; a driving licence has an expiry; a TIN has neither. Four
-- sets of those flattened onto one row is a row that is mostly null and a form
-- that has to know which columns belong together.
--
-- A customer belongs to a branch. That is what makes the scope rules from
-- Milestone 7 do something: a branch-scoped officer reads the customers of the
-- branches they are posted to, and nobody else's.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- The master record
-- ---------------------------------------------------------------------------
CREATE TABLE customer.t_customer (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),

    -- The number a branch quotes on the phone. A business identifier, unlike
    -- the surrogate above, and printed on statements.
    customer_id           VARCHAR(20)   NOT NULL,

    customer_type         VARCHAR(20)   NOT NULL,

    -- Where the relationship is held. Null only while a record is being taken
    -- on by head office and not yet attached anywhere.
    home_branch_id        UUID          REFERENCES organization.t_org_unit (id) ON DELETE RESTRICT,

    -- Set once the customer can sign in. Null for a customer the bank holds a
    -- record of but who has never used the app.
    user_id               UUID          REFERENCES auth.t_user (id) ON DELETE SET NULL,

    -- ---- identity ----
    full_name             VARCHAR(160)  NOT NULL,
    father_name           VARCHAR(160),
    mother_name           VARCHAR(160),
    spouse_name           VARCHAR(160),
    date_of_birth         DATE,
    gender                VARCHAR(10),
    nationality           VARCHAR(60)   NOT NULL DEFAULT 'Bangladeshi',
    marital_status        VARCHAR(20),
    education_level       VARCHAR(40),
    residence_status      VARCHAR(20)   NOT NULL DEFAULT 'RESIDENT',

    -- ---- contact ----
    mobile                VARCHAR(20)   NOT NULL,
    email                 VARCHAR(160),

    -- ---- occupation ----
    occupation            VARCHAR(80),
    designation           VARCHAR(80),
    employer_name         VARCHAR(160),

    -- ---- money ----
    -- NUMERIC(20,4) throughout, per the platform rule. Never float, and never
    -- a Java double: a rounding error in a customer's declared income becomes a
    -- rounding error in the limit it justifies.
    monthly_income        NUMERIC(20,4),
    other_monthly_income  NUMERIC(20,4),
    source_of_income      VARCHAR(120),
    source_of_funds       VARCHAR(120),
    net_worth             NUMERIC(20,4),
    existing_liabilities  NUMERIC(20,4),
    -- VARCHAR rather than CHAR even though a currency code is always three
    -- characters: CHAR is blank-padded in PostgreSQL, and a padded 'BDT ' does
    -- not equal 'BDT' in a comparison.
    currency              VARCHAR(3)    NOT NULL DEFAULT 'BDT',

    -- ---- standing ----
    risk_profile          VARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',

    -- Owned by the KYC module from Milestone 10; carried here because the
    -- eligibility rules read it off the customer, not out of a provider.
    kyc_status            VARCHAR(20)   NOT NULL DEFAULT 'PENDING',

    status                VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    onboarded_on          DATE          NOT NULL DEFAULT CURRENT_DATE,

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by            VARCHAR(64)   NOT NULL DEFAULT 'system',
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ux_customer_customer_id UNIQUE (customer_id),
    CONSTRAINT ux_customer_user UNIQUE (user_id),
    CONSTRAINT ck_customer_type CHECK (customer_type IN (
        'INDIVIDUAL', 'JOINT', 'MINOR', 'GUARDIAN', 'AUTHORIZED_PERSON',
        'BUSINESS', 'SOLE_PROPRIETOR')),
    CONSTRAINT ck_customer_gender CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER')),
    CONSTRAINT ck_customer_marital CHECK (marital_status IS NULL OR marital_status IN (
        'SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED')),
    CONSTRAINT ck_customer_residence CHECK (residence_status IN ('RESIDENT', 'NON_RESIDENT')),
    CONSTRAINT ck_customer_risk CHECK (risk_profile IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_customer_kyc CHECK (kyc_status IN ('PENDING', 'IN_PROGRESS', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'DORMANT', 'CLOSED')),
    -- Money is never negative on a customer record. A negative net worth is
    -- real, but it is recorded as a liability, not as a signed asset.
    CONSTRAINT ck_customer_amounts CHECK (
        (monthly_income IS NULL OR monthly_income >= 0)
        AND (other_monthly_income IS NULL OR other_monthly_income >= 0)
        AND (net_worth IS NULL OR net_worth >= 0)
        AND (existing_liabilities IS NULL OR existing_liabilities >= 0))
);

CREATE INDEX ix_customer_branch ON customer.t_customer (home_branch_id);
CREATE INDEX ix_customer_mobile ON customer.t_customer (mobile);
CREATE INDEX ix_customer_name ON customer.t_customer (lower(full_name));

COMMENT ON TABLE  customer.t_customer IS 'The customer master: one row per person or business the bank holds a relationship with';
COMMENT ON COLUMN customer.t_customer.customer_id IS 'The number a branch quotes; business identifier, not the surrogate key';
COMMENT ON COLUMN customer.t_customer.kyc_status IS 'Written by the KYC module from Milestone 10; read by the eligibility rules';

-- ---------------------------------------------------------------------------
-- Addresses
-- ---------------------------------------------------------------------------
CREATE TABLE customer.t_customer_address (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id   UUID         NOT NULL REFERENCES customer.t_customer (id) ON DELETE CASCADE,

    -- A customer has a present address and a permanent one, and they are
    -- routinely different in Bangladesh: people bank where they work and are
    -- registered where their family is.
    address_type  VARCHAR(20)  NOT NULL,

    line1         VARCHAR(255) NOT NULL,
    line2         VARCHAR(255),
    city          VARCHAR(80),
    district      VARCHAR(80),
    postal_code   VARCHAR(20),
    country       VARCHAR(60)  NOT NULL DEFAULT 'Bangladesh',

    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_address_type CHECK (address_type IN ('PRESENT', 'PERMANENT', 'BUSINESS')),
    CONSTRAINT ux_address_per_type UNIQUE (customer_id, address_type)
);

CREATE INDEX ix_address_customer ON customer.t_customer_address (customer_id);

COMMENT ON TABLE customer.t_customer_address IS 'Present, permanent and business addresses; one of each per customer';

-- ---------------------------------------------------------------------------
-- Identification documents
-- ---------------------------------------------------------------------------
CREATE TABLE customer.t_customer_identification (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id    UUID         NOT NULL REFERENCES customer.t_customer (id) ON DELETE CASCADE,

    id_type        VARCHAR(30)  NOT NULL,
    id_number      VARCHAR(60)  NOT NULL,

    issue_date     DATE,
    expiry_date    DATE,
    issue_place    VARCHAR(120),

    -- Set by the KYC module once a document has actually been checked against
    -- an authority. Unverified until then, whatever the customer typed.
    verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    verified_at    TIMESTAMPTZ,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_identification_type CHECK (id_type IN (
        'NID', 'TIN', 'PASSPORT', 'DRIVING_LICENCE', 'BIRTH_CERTIFICATE')),
    CONSTRAINT ck_identification_dates CHECK (
        expiry_date IS NULL OR issue_date IS NULL OR expiry_date >= issue_date),
    CONSTRAINT ux_identification_per_type UNIQUE (customer_id, id_type)
);

CREATE INDEX ix_identification_customer ON customer.t_customer_identification (customer_id);

-- A national ID identifies exactly one person. Two customer records sharing one
-- is either a duplicate or a fraud, and both are worth failing an insert over.
CREATE UNIQUE INDEX ux_identification_nid
    ON customer.t_customer_identification (id_number) WHERE id_type = 'NID';

COMMENT ON TABLE customer.t_customer_identification IS 'NID, TIN, passport and licence, each with its own dates and verification state';

-- ---------------------------------------------------------------------------
-- Seed: the permission this milestone introduces.
--
-- Granted to every role. What a person actually sees is narrowed by their
-- organisational scope at query time, not by withholding the permission: a
-- Field Officer needs customer.view to do their job, and the branch filter is
-- what stops them reading another branch's book.
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_permission (code, name, description, module) VALUES
    ('customer.view', 'View customers',
     'Read the customer master, within the reader''s organisational scope', 'CUSTOMER');

INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'customer.view';
