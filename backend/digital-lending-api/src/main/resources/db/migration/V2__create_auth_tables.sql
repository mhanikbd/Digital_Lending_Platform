-- ============================================================================
-- V2 : Authentication.
--
-- One identity table serves all three actors the platform authenticates -
-- bank users, customers and field officers - because the mechanics of a login
-- attempt, a lock-out and a session are identical for all of them. What differs
-- is the credential they present, which is why the secret lives in its own
-- table and is typed.
--
-- Roles and permissions are deliberately NOT created here. Authentication
-- answers "who is this"; authorisation answers "what may they do", and that is
-- Milestone 6. Creating those tables now would be creating them speculatively.
--
-- OTP challenges are not stored here either: they live in Redis, which is where
-- this platform keeps short-lived state.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Identity
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_user (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- BANK_USER | CUSTOMER | FIELD_OFFICER. Held as text with a check rather
    -- than a native enum: adding an actor type must not require a table rewrite.
    user_type             VARCHAR(20)  NOT NULL,

    -- Employee id for staff, mobile number for customers. Unique per type, so a
    -- customer's mobile can never collide with an employee id.
    username              VARCHAR(64)  NOT NULL,

    display_name          VARCHAR(160) NOT NULL,
    email                 VARCHAR(160),
    mobile                VARCHAR(20),

    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    mfa_enabled           BOOLEAN      NOT NULL DEFAULT FALSE,

    -- Brute-force control. Authoritative here rather than in Redis: a lock must
    -- survive a cache flush.
    failed_attempts       SMALLINT     NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,

    last_login_at         TIMESTAMPTZ,
    must_change_secret    BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by            VARCHAR(64)  NOT NULL DEFAULT 'system',
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by            VARCHAR(64)  NOT NULL DEFAULT 'system',
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_user_type   CHECK (user_type IN ('BANK_USER', 'CUSTOMER', 'FIELD_OFFICER')),
    CONSTRAINT ck_user_status CHECK (status IN ('ACTIVE', 'LOCKED', 'SUSPENDED', 'DISABLED')),
    CONSTRAINT ck_user_failed_attempts CHECK (failed_attempts >= 0)
);

CREATE UNIQUE INDEX ux_user_type_username ON auth.t_user (user_type, lower(username));
CREATE INDEX ix_user_status ON auth.t_user (status) WHERE status <> 'ACTIVE';

COMMENT ON TABLE  auth.t_user IS 'Every identity the platform authenticates, whatever the channel';
COMMENT ON COLUMN auth.t_user.username IS 'Employee id for staff, mobile number for customers; unique within a user type';
COMMENT ON COLUMN auth.t_user.locked_until IS 'Set when failed_attempts crosses the configured threshold; null once served';

-- ---------------------------------------------------------------------------
-- Secrets
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_user_credential (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES auth.t_user (id) ON DELETE CASCADE,

    -- PASSWORD for staff, PIN for customers. One identity may hold both.
    credential_type   VARCHAR(20)  NOT NULL,

    -- BCrypt output, which carries its own salt and cost factor. Never a
    -- reversible form, and never logged.
    secret_hash       VARCHAR(120) NOT NULL,
    algorithm         VARCHAR(20)  NOT NULL DEFAULT 'BCRYPT',

    rotated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_credential_type CHECK (credential_type IN ('PASSWORD', 'PIN')),
    CONSTRAINT ux_credential_per_type UNIQUE (user_id, credential_type)
);

COMMENT ON TABLE auth.t_user_credential IS 'Hashed secrets, one row per credential type per identity';

-- ---------------------------------------------------------------------------
-- Devices
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_device (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES auth.t_user (id) ON DELETE CASCADE,

    -- Client-generated, stable for the install. Bound to one identity, so a
    -- customer's PIN is useless from a handset that was never registered.
    device_id      VARCHAR(128) NOT NULL,
    platform       VARCHAR(20),
    model          VARCHAR(80),
    os_version     VARCHAR(40),
    app_version    VARCHAR(40),

    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    biometric_enabled BOOLEAN   NOT NULL DEFAULT FALSE,

    bound_at       TIMESTAMPTZ,
    last_seen_at   TIMESTAMPTZ,

    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version        BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT ck_device_status CHECK (status IN ('PENDING', 'TRUSTED', 'BLOCKED')),
    CONSTRAINT ux_device_per_user UNIQUE (user_id, device_id)
);

CREATE INDEX ix_device_user ON auth.t_device (user_id);

COMMENT ON TABLE auth.t_device IS 'Registered handsets; a customer PIN is only accepted from a TRUSTED device';

-- ---------------------------------------------------------------------------
-- Sessions
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_session (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES auth.t_user (id) ON DELETE CASCADE,
    device_id           UUID         REFERENCES auth.t_device (id) ON DELETE SET NULL,

    -- Only the hash is stored. A database reader must not be able to replay a
    -- refresh token, which is the whole point of storing them server-side.
    --
    -- VARCHAR rather than CHAR even though the value is always 64 characters:
    -- CHAR is blank-padded in PostgreSQL, and a lookup is an exact match on
    -- this column.
    refresh_token_hash  VARCHAR(64)  NOT NULL,

    issued_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    last_used_at        TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    revoked_reason      VARCHAR(40),

    ip_address          VARCHAR(45),
    user_agent          VARCHAR(255),

    CONSTRAINT ux_session_refresh_hash UNIQUE (refresh_token_hash)
);

CREATE INDEX ix_session_user_active ON auth.t_session (user_id) WHERE revoked_at IS NULL;

COMMENT ON TABLE  auth.t_session IS 'Refresh-token sessions; the access token itself is stateless';
COMMENT ON COLUMN auth.t_session.refresh_token_hash IS 'SHA-256 of the opaque refresh token, hex encoded';

-- ---------------------------------------------------------------------------
-- Login history
-- ---------------------------------------------------------------------------
CREATE TABLE auth.t_login_history (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id             UUID         REFERENCES auth.t_user (id) ON DELETE SET NULL,

    -- Recorded even when no identity matched, which is exactly the case an
    -- investigator cares about. Never carries the secret that was presented.
    username_attempted  VARCHAR(64)  NOT NULL,
    user_type           VARCHAR(20),

    outcome             VARCHAR(30)  NOT NULL,
    reason              VARCHAR(120),

    ip_address          VARCHAR(45),
    user_agent          VARCHAR(255),
    device_id           VARCHAR(128),
    correlation_id      VARCHAR(64),

    occurred_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT ck_login_outcome CHECK (outcome IN (
        'SUCCESS', 'UNKNOWN_USER', 'BAD_CREDENTIALS', 'ACCOUNT_LOCKED',
        'ACCOUNT_NOT_ACTIVE', 'MFA_REQUIRED', 'MFA_FAILED', 'DEVICE_NOT_TRUSTED'))
);

CREATE INDEX ix_login_history_user_time ON auth.t_login_history (user_id, occurred_at DESC);
CREATE INDEX ix_login_history_time ON auth.t_login_history (occurred_at DESC);

COMMENT ON TABLE auth.t_login_history IS 'Immutable record of every authentication attempt, successful or not';
