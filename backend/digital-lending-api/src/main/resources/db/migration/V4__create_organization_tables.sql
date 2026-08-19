-- ============================================================================
-- V4 : The bank as an organisation.
--
-- The specification names nine kinds of unit: bank, zone, region, branch,
-- department, business unit, credit unit, personal processing centre and credit
-- administration department. They are not nine tables. Every one of them is a
-- named node with a parent, a code and a status, and a bank that opens a tenth
-- kind next year should not need a migration to do it - which is the platform
-- rule about configuration over hard-coding applied to its own shape.
--
-- So: one catalogue of unit types, one self-referencing tree of units, and one
-- table saying which people work in which of them.
--
-- The specification lists this assignment table as T_USER_BRANCH. It is called
-- t_user_org_unit here because it has to hold more than branches: the examples
-- in the same section put a Head of Credit Risk Management at head office and a
-- Sourcing Officer across several branches. A column named branch_id holding a
-- department would be a lie in the schema, and two tables for one relation would
-- be worse.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- What kinds of unit this bank has
-- ---------------------------------------------------------------------------
CREATE TABLE organization.t_org_unit_type (
    code               VARCHAR(30)  PRIMARY KEY,
    name               VARCHAR(120) NOT NULL,
    description        VARCHAR(255),

    -- The parent a unit of this type normally hangs from. Advisory: it is what
    -- an administration screen offers by default, not a constraint. Banks vary
    -- on whether branches sit under a region or straight under a zone, and a
    -- schema that insists on one shape is a schema that some bank cannot use.
    parent_type_code   VARCHAR(30)  REFERENCES organization.t_org_unit_type (code),

    -- Depth in the conventional tree, for ordering an administration screen.
    hierarchy_level    SMALLINT     NOT NULL,

    -- Does this type serve customers over a counter? The distinction the
    -- specification draws between branch-level and head-office-level authority
    -- rests on this rather than on the type code, so a bank adding a
    -- customer-facing type does not have to be special-cased in code.
    is_customer_facing BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

COMMENT ON TABLE  organization.t_org_unit_type IS 'The kinds of unit this bank is made of';
COMMENT ON COLUMN organization.t_org_unit_type.parent_type_code IS 'Conventional parent; advisory, not enforced against t_org_unit';

-- ---------------------------------------------------------------------------
-- The tree itself
-- ---------------------------------------------------------------------------
CREATE TABLE organization.t_org_unit (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    unit_type_code  VARCHAR(30)  NOT NULL REFERENCES organization.t_org_unit_type (code),

    -- Null only for the root. A bank has no parent.
    parent_id       UUID         REFERENCES organization.t_org_unit (id) ON DELETE RESTRICT,

    -- What the bank calls it. Branch codes appear on statements and in the core
    -- banking system, so this is a business identifier, not a surrogate.
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(160) NOT NULL,
    short_name      VARCHAR(60),

    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    -- A branch that closes stops accepting applications but keeps its loans, so
    -- units are dated rather than deleted.
    effective_from  DATE         NOT NULL DEFAULT CURRENT_DATE,
    effective_to    DATE,

    address_line    VARCHAR(255),
    city            VARCHAR(80),
    district        VARCHAR(80),
    phone           VARCHAR(40),
    email           VARCHAR(160),

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(64)  NOT NULL DEFAULT 'system',
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ux_org_unit_code UNIQUE (code),
    CONSTRAINT ck_org_unit_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_org_unit_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    -- Cheap guard against the most obvious cycle. Deeper ones are the service's
    -- job, because SQL cannot see them at insert time.
    CONSTRAINT ck_org_unit_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX ix_org_unit_parent ON organization.t_org_unit (parent_id);
CREATE INDEX ix_org_unit_type ON organization.t_org_unit (unit_type_code);
CREATE INDEX ix_org_unit_active ON organization.t_org_unit (status) WHERE status = 'ACTIVE';

COMMENT ON TABLE organization.t_org_unit IS 'Every unit of the bank, from the institution down to a branch';

-- ---------------------------------------------------------------------------
-- Who works where
-- ---------------------------------------------------------------------------
CREATE TABLE organization.t_user_org_unit (
    user_id      UUID        NOT NULL REFERENCES auth.t_user (id) ON DELETE CASCADE,
    org_unit_id  UUID        NOT NULL REFERENCES organization.t_org_unit (id) ON DELETE RESTRICT,

    -- Where they sit, as opposed to where they may also act. A Sourcing Officer
    -- covering three branches is based at one of them.
    is_primary   BOOLEAN     NOT NULL DEFAULT FALSE,

    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by  VARCHAR(64) NOT NULL DEFAULT 'system',

    PRIMARY KEY (user_id, org_unit_id)
);

CREATE INDEX ix_user_org_unit_unit ON organization.t_user_org_unit (org_unit_id);

-- One home, however many postings. Partial unique index rather than a check,
-- because the rule is about the set of rows for a user, not about one row.
CREATE UNIQUE INDEX ux_user_primary_org_unit
    ON organization.t_user_org_unit (user_id) WHERE is_primary;

COMMENT ON TABLE organization.t_user_org_unit IS 'Postings. The specification calls this T_USER_BRANCH; it holds head-office units too';

-- ---------------------------------------------------------------------------
-- Seed: the unit types the specification names.
--
-- The types are seeded because they are the product definition. The units are
-- not: a bank fills in its own zones and branches, and shipping an invented
-- branch would be shipping fiction.
-- ---------------------------------------------------------------------------
INSERT INTO organization.t_org_unit_type
    (code, name, description, parent_type_code, hierarchy_level, is_customer_facing) VALUES
    ('BANK',          'Bank',          'The institution itself',                     NULL,     0, FALSE),
    ('ZONE',          'Zone',          'A group of regions',                         'BANK',   1, FALSE),
    ('REGION',        'Region',        'A group of branches',                        'ZONE',   2, FALSE),
    ('BRANCH',        'Branch',        'A customer-facing office',                   'REGION', 3, TRUE),
    ('DEPARTMENT',    'Department',    'A head office department',                   'BANK',   1, FALSE),
    ('BUSINESS_UNIT', 'Business Unit', 'A head office business line',                'BANK',   1, FALSE),
    ('CREDIT_UNIT',   'Credit Unit',   'A head office credit function',              'BANK',   1, FALSE),
    ('PPC',           'Personal Processing Centre',
                      'Centralised processing of personal lending applications',     'BANK',   1, FALSE),
    ('CAD',           'Credit Administration Department',
                      'Disburses approved loans and reconciles with core banking',   'BANK',   1, FALSE);

-- ---------------------------------------------------------------------------
-- Seed: the permission this milestone introduces, and its grants.
--
-- Everyone may read the organisation: a Field Officer who cannot see their own
-- branch cannot be told which branch they are filing against. What each person
-- actually sees is narrowed by their role scope at query time, not by hiding
-- the endpoint.
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_permission (code, name, description, module) VALUES
    ('organization.view', 'View the organisation',
     'Read the bank hierarchy and the units a person is posted to', 'ORGANIZATION');

INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'organization.view';
