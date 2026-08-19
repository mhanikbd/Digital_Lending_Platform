-- ============================================================================
-- V3 : Role-based access control.
--
-- Authentication (V2) answers "who is this". This answers "what may they do",
-- and it answers it from the database rather than from code. The platform rule
-- is explicit: never write `if (role.equals("BM"))`. A role is a row, a
-- permission is a row, and the link between them is a row, so a bank can change
-- who may do what without a deployment.
--
-- The thirteen lending roles are the ones named in the specification. They are
-- seeded because they are part of the product definition, not because a
-- developer happened to need them - unlike the local bootstrap user, which is
-- created by a profile-guarded runner and never by a migration.
--
-- Branch and organisation scoping (t_user_branch) is deliberately absent: the
-- organisation tree arrives in Milestone 7, and a table keyed to branches that
-- do not exist yet would be a table of nothing.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- What can be done
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_permission (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Dotted, module-first, and stable. This string appears in @PreAuthorize
    -- and in access tokens, so renaming one is an API change.
    code         VARCHAR(80)  NOT NULL,

    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(255),

    -- Which part of the platform the permission belongs to, so an admin screen
    -- can group several hundred of them sensibly.
    module       VARCHAR(40)  NOT NULL,

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ux_permission_code UNIQUE (code)
);

COMMENT ON TABLE  auth.t_permission IS 'The catalogue of things that can be permitted';
COMMENT ON COLUMN auth.t_permission.code IS 'Stable identifier used in code and in tokens; renaming is a breaking change';

-- ---------------------------------------------------------------------------
-- Who can be something
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_role (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- FO, SO, BM, CA and so on. Short because it is what people call the job.
    code         VARCHAR(40)  NOT NULL,

    name         VARCHAR(120) NOT NULL,
    description  VARCHAR(255),

    -- How far a holder of this role can see once the organisation tree exists:
    -- their own branch, a region, or the whole bank. Recorded now so that
    -- Milestone 7 has somewhere to attach, and read by nothing until then.
    scope_level  VARCHAR(20)  NOT NULL DEFAULT 'BRANCH',

    -- A seeded role is part of the product. A bank may change its permissions
    -- but may not delete it, or the workflow it appears in loses a participant.
    is_system    BOOLEAN      NOT NULL DEFAULT FALSE,

    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by   VARCHAR(64)  NOT NULL DEFAULT 'system',
    version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ux_role_code UNIQUE (code),
    CONSTRAINT ck_role_scope CHECK (scope_level IN ('BRANCH', 'REGION', 'HEAD_OFFICE')),
    CONSTRAINT ck_role_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

COMMENT ON TABLE auth.t_role IS 'Configurable roles; the thirteen lending roles are seeded as system roles';

-- ---------------------------------------------------------------------------
-- Which role may do what
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_role_permission (
    role_id        UUID        NOT NULL REFERENCES auth.t_role (id) ON DELETE CASCADE,
    permission_id  UUID        NOT NULL REFERENCES auth.t_permission (id) ON DELETE CASCADE,
    granted_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by     VARCHAR(64) NOT NULL DEFAULT 'system',

    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX ix_role_permission_permission ON auth.t_role_permission (permission_id);

COMMENT ON TABLE auth.t_role_permission IS 'The grant. Changing a bank policy is an insert or a delete here, never a deployment';

-- ---------------------------------------------------------------------------
-- Which person holds which role
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_user_role (
    user_id      UUID        NOT NULL REFERENCES auth.t_user (id) ON DELETE CASCADE,
    role_id      UUID        NOT NULL REFERENCES auth.t_role (id) ON DELETE RESTRICT,
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by  VARCHAR(64) NOT NULL DEFAULT 'system',

    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX ix_user_role_role ON auth.t_user_role (role_id);

COMMENT ON TABLE auth.t_user_role IS 'Role assignment. A person may hold several, and their permissions are the union';

-- ---------------------------------------------------------------------------
-- Seed: the permission catalogue
--
-- Only permissions with something behind them today. The catalogue grows with
-- each module that introduces a capability; seeding permissions for modules
-- that do not exist would be seeding fiction.
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_permission (code, name, description, module) VALUES
    ('system.health.view', 'View system health',
     'See whether the database, cache and object storage are reachable', 'PLATFORM'),
    ('admin.role.view', 'View roles and permissions',
     'Read the role catalogue and what each role is permitted to do', 'ADMIN'),
    ('admin.user.view', 'View bank users',
     'Read the list of bank users and the roles they hold', 'ADMIN');

-- ---------------------------------------------------------------------------
-- Seed: the roles named in the specification, plus the administrator that
-- role administration itself requires.
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_role (code, name, description, scope_level, is_system) VALUES
    ('ADMIN', 'System Administrator', 'Configures users, roles and platform settings', 'HEAD_OFFICE', TRUE),
    ('FO',    'Field Officer',        'Originates applications on behalf of customers', 'BRANCH', TRUE),
    ('SO',    'Sourcing Officer',     'Reviews and recommends originated applications', 'BRANCH', TRUE),
    ('BM',    'Branch Manager',       'Recommends applications from their own branch', 'BRANCH', TRUE),
    ('BOM',   'Branch Operations Manager', 'Branch-side operational recommendation', 'BRANCH', TRUE),
    ('PPC',   'Personal Processing Centre', 'Centralised processing recommendation', 'HEAD_OFFICE', TRUE),
    ('MIS',   'Management Information System', 'Allocates and dispatches applications at head office', 'HEAD_OFFICE', TRUE),
    ('CA',    'Credit Analyst',       'Analyses credit and raises queries', 'HEAD_OFFICE', TRUE),
    ('RM',    'Relationship Manager', 'First delegated approval tier', 'REGION', TRUE),
    ('UH',    'Unit Head',            'Second delegated approval tier', 'REGION', TRUE),
    ('HOCRM', 'Head of Credit Risk Management', 'Third delegated approval tier', 'HEAD_OFFICE', TRUE),
    ('CEO',   'Chief Executive Officer', 'Highest delegated approval tier', 'HEAD_OFFICE', TRUE),
    ('MD',    'Managing Director',    'Highest delegated approval tier', 'HEAD_OFFICE', TRUE),
    ('CAD',   'Credit Administration Department', 'Disburses approved loans and reconciles with core banking', 'HEAD_OFFICE', TRUE);

-- ---------------------------------------------------------------------------
-- Seed: grants.
--
-- Every staff role can see whether the platform is up, because a person who
-- cannot tell a broken system from a slow one raises the wrong support call.
-- Administration is the administrator's alone.
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'system.health.view';

INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE r.code = 'ADMIN'
  AND p.code IN ('admin.role.view', 'admin.user.view');
