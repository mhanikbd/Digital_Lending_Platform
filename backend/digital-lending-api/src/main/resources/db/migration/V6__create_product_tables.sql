-- ============================================================================
-- V6 : Loan products, and the versions that actually hold their terms.
--
-- The split is the whole point of Milestone 14. A product is an identity - the
-- thing a bank markets, called e-Loan - and it barely changes. A version is the
-- terms it was sold on: the rate, the limits, the fees, the tenures. Those
-- change constantly, and an application approved in March must keep being judged
-- by March's terms however many times the product is repriced afterwards.
--
-- So nothing that can change is on t_loan_product, and a live version is never
-- edited in place. Repricing means a new version.
--
-- Every amount is NUMERIC(20,4) and every rate NUMERIC(9,6). A rate is stored as
-- a percentage per annum - 9.000000 means nine percent - because that is what a
-- product sheet says, and converting once at the point of calculation is safer
-- than storing a fraction that half the code forgets to multiply.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- The product: an identity, and nothing that gets repriced
-- ---------------------------------------------------------------------------
CREATE TABLE product.t_loan_product (
    id            UUID          PRIMARY KEY DEFAULT gen_random_uuid(),

    code          VARCHAR(30)   NOT NULL,
    name          VARCHAR(120)  NOT NULL,

    -- The bank operates in Bangladesh and its customers do not all read English.
    name_bn       VARCHAR(160),

    product_type  VARCHAR(30)   NOT NULL,
    category      VARCHAR(30)   NOT NULL,
    description   VARCHAR(500),

    status        VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',

    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by    VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by    VARCHAR(64)   NOT NULL DEFAULT 'system',
    version       BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ux_loan_product_code UNIQUE (code),
    CONSTRAINT ck_loan_product_type CHECK (product_type IN ('TERM_LOAN', 'CREDIT_CARD', 'OVERDRAFT')),
    CONSTRAINT ck_loan_product_category CHECK (category IN (
        'PERSONAL', 'SME', 'HOME', 'AUTO', 'STUDENT', 'CARD')),
    CONSTRAINT ck_loan_product_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

COMMENT ON TABLE product.t_loan_product IS 'A product the bank markets. Holds nothing that gets repriced';

-- ---------------------------------------------------------------------------
-- The version: everything a decision is made against
-- ---------------------------------------------------------------------------
CREATE TABLE product.t_loan_product_version (
    id                    UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id            UUID          NOT NULL REFERENCES product.t_loan_product (id) ON DELETE RESTRICT,

    version_no            INTEGER       NOT NULL,

    -- DRAFT is editable. ACTIVE is not: it is being lent against. RETIRED is
    -- kept because applications still reference it.
    status                VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',

    effective_from        DATE          NOT NULL,
    effective_to          DATE,

    customer_segment      VARCHAR(40)   NOT NULL DEFAULT 'ANY',
    secured               BOOLEAN       NOT NULL DEFAULT FALSE,
    currency              VARCHAR(3)    NOT NULL DEFAULT 'BDT',

    min_amount            NUMERIC(20,4) NOT NULL,
    max_amount            NUMERIC(20,4) NOT NULL,
    min_tenure_months     SMALLINT      NOT NULL,
    max_tenure_months     SMALLINT      NOT NULL,

    -- FLAT charges interest on the original principal for the whole term.
    -- REDUCING_BALANCE charges it on what is still owed. They give very
    -- different totals for the same headline rate, which is exactly why the
    -- method is configuration and not an assumption in the calculator.
    interest_method       VARCHAR(30)   NOT NULL,
    interest_rate         NUMERIC(9,6)  NOT NULL,

    grace_period_days     SMALLINT      NOT NULL DEFAULT 0,
    repayment_frequency   VARCHAR(20)   NOT NULL DEFAULT 'MONTHLY',

    collateral_required   BOOLEAN       NOT NULL DEFAULT FALSE,
    guarantor_required    BOOLEAN       NOT NULL DEFAULT FALSE,

    -- ---- limit parameters, read by the amount engine ----
    -- How many months of net income the bank will lend. The single most
    -- adjusted number in consumer lending, and therefore never a constant.
    income_multiple       NUMERIC(9,4),

    -- The share of income that may go to servicing debt, as a fraction:
    -- 0.5000 is fifty percent.
    max_dbr               NUMERIC(5,4),

    -- A cap the regulator sets rather than the bank. Separate from max_amount
    -- so that a change of policy and a change of regulation are distinguishable.
    regulatory_max_amount NUMERIC(20,4),

    -- What the platform offers a qualifying customer, as a share of what they
    -- could have. Banks rarely lead with the ceiling: a customer offered their
    -- absolute maximum has no headroom left and defaults more often. The share
    -- is marketing policy, so it is configured per version like everything else.
    recommended_ratio     NUMERIC(5,4)  NOT NULL DEFAULT 0.7000,

    -- The most total debt the bank is prepared to lend a borrower into,
    -- counting what they already owe elsewhere. Null means the product sets no
    -- such ceiling, which is not the same as a ceiling of zero - and is the
    -- right default, because an unsecured personal loan is not normally refused
    -- to somebody merely for holding a mortgage. Affordability is the debt
    -- burden ratio's job; this is a concentration control on top of it.
    max_total_exposure    NUMERIC(20,4),

    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by            VARCHAR(64)   NOT NULL DEFAULT 'system',
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by            VARCHAR(64)   NOT NULL DEFAULT 'system',
    version               BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT ux_product_version UNIQUE (product_id, version_no),
    CONSTRAINT ck_version_status CHECK (status IN ('DRAFT', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_version_method CHECK (interest_method IN ('FLAT', 'REDUCING_BALANCE', 'EFFECTIVE')),
    CONSTRAINT ck_version_frequency CHECK (repayment_frequency IN ('MONTHLY', 'QUARTERLY', 'HALF_YEARLY', 'YEARLY')),
    CONSTRAINT ck_version_amounts CHECK (min_amount > 0 AND max_amount >= min_amount),
    CONSTRAINT ck_version_tenures CHECK (min_tenure_months > 0 AND max_tenure_months >= min_tenure_months),
    CONSTRAINT ck_version_rate CHECK (interest_rate >= 0),
    CONSTRAINT ck_version_dbr CHECK (max_dbr IS NULL OR (max_dbr > 0 AND max_dbr <= 1)),
    CONSTRAINT ck_version_recommended CHECK (recommended_ratio > 0 AND recommended_ratio <= 1),
    CONSTRAINT ck_version_exposure CHECK (max_total_exposure IS NULL OR max_total_exposure >= 0),
    CONSTRAINT ck_version_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
);

CREATE INDEX ix_product_version_product ON product.t_loan_product_version (product_id);

-- At most one version of a product may be live at a time. A second would make
-- "which terms is this application judged under" a question with two answers.
CREATE UNIQUE INDEX ux_product_single_active_version
    ON product.t_loan_product_version (product_id) WHERE status = 'ACTIVE';

COMMENT ON TABLE  product.t_loan_product_version IS 'The terms. An application records the version it was judged under and keeps it';
COMMENT ON COLUMN product.t_loan_product_version.interest_rate IS 'Percent per annum, so 9.000000 is nine percent';
COMMENT ON COLUMN product.t_loan_product_version.max_dbr IS 'Fraction of income that may service debt, so 0.5000 is fifty percent';
COMMENT ON COLUMN product.t_loan_product_version.recommended_ratio IS 'Share of the eligible maximum that is offered, so 0.7000 is seventy percent';
COMMENT ON COLUMN product.t_loan_product_version.max_total_exposure IS 'Ceiling on total borrowing including what is owed elsewhere. Null means no such ceiling';

-- ---------------------------------------------------------------------------
-- The tenures actually offered
-- ---------------------------------------------------------------------------
CREATE TABLE product.t_product_tenure (
    product_version_id  UUID     NOT NULL REFERENCES product.t_loan_product_version (id) ON DELETE CASCADE,
    tenure_months       SMALLINT NOT NULL,

    PRIMARY KEY (product_version_id, tenure_months),
    CONSTRAINT ck_tenure_positive CHECK (tenure_months > 0)
);

COMMENT ON TABLE product.t_product_tenure IS 'The discrete tenures a customer may pick, which is not every month between the bounds';

-- ---------------------------------------------------------------------------
-- Fees
-- ---------------------------------------------------------------------------
CREATE TABLE product.t_product_fee (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    product_version_id  UUID          NOT NULL REFERENCES product.t_loan_product_version (id) ON DELETE CASCADE,

    fee_code            VARCHAR(30)   NOT NULL,
    name                VARCHAR(120)  NOT NULL,

    calculation_method  VARCHAR(30)   NOT NULL,

    -- One of the two is used, decided by calculation_method. Both nullable
    -- rather than one overloaded column, so a flat amount and a rate are never
    -- confused for one another.
    flat_amount         NUMERIC(20,4),
    rate                NUMERIC(9,6),

    -- VAT is charged on the fee, not on the loan. Fifteen percent in Bangladesh,
    -- but stored rather than assumed.
    vat_rate            NUMERIC(9,6)  NOT NULL DEFAULT 0,

    collected_at        VARCHAR(20)   NOT NULL DEFAULT 'DISBURSEMENT',
    mandatory           BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ux_product_fee UNIQUE (product_version_id, fee_code),
    CONSTRAINT ck_fee_method CHECK (calculation_method IN ('FLAT', 'PERCENT_OF_PRINCIPAL')),
    CONSTRAINT ck_fee_collected CHECK (collected_at IN ('DISBURSEMENT', 'EMI', 'ON_DEFAULT')),
    CONSTRAINT ck_fee_value CHECK (
        (calculation_method = 'FLAT' AND flat_amount IS NOT NULL AND flat_amount >= 0)
        OR (calculation_method = 'PERCENT_OF_PRINCIPAL' AND rate IS NOT NULL AND rate >= 0))
);

COMMENT ON TABLE product.t_product_fee IS 'Processing fee, insurance, penalties. Configured per version, never in code';

-- ---------------------------------------------------------------------------
-- Risk-graded limits
-- ---------------------------------------------------------------------------
CREATE TABLE product.t_product_risk_limit (
    product_version_id  UUID          NOT NULL REFERENCES product.t_loan_product_version (id) ON DELETE CASCADE,

    -- Matches the customer's risk profile today. When the scorecard arrives it
    -- writes the same grades, so this table does not move.
    risk_profile        VARCHAR(20)   NOT NULL,
    max_amount          NUMERIC(20,4) NOT NULL,

    PRIMARY KEY (product_version_id, risk_profile),
    CONSTRAINT ck_risk_limit_profile CHECK (risk_profile IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_risk_limit_amount CHECK (max_amount >= 0)
);

COMMENT ON TABLE product.t_product_risk_limit IS 'What each risk grade may borrow. A grade with no row here is not capped by grade';

-- ---------------------------------------------------------------------------
-- Permissions introduced by this milestone
-- ---------------------------------------------------------------------------
INSERT INTO auth.t_permission (code, name, description, module) VALUES
    ('product.view', 'View loan products',
     'Read the product catalogue and the terms of each version', 'PRODUCT'),
    ('eligibility.check', 'Check eligibility',
     'Run the eligibility rules for a customer against a product', 'PRODUCT'),
    ('product.configure', 'Configure loan products',
     'Draft, amend, activate and retire product versions', 'PRODUCT'),
    ('product.price', 'Quote a negotiated rate',
     'Produce a quotation at a rate other than the published one', 'PRODUCT');

-- Reading the catalogue and checking eligibility are what the platform is for:
-- every staff role does both.
INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code IN ('product.view', 'eligibility.check');

-- Changing the terms is not. A product version decides what every subsequent
-- application is judged by, so it sits with administration until the maker and
-- checker of Milestone 21 exist to divide it properly.
INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'product.configure' AND r.code = 'ADMIN';

-- Departing from the published rate is a concession, and a concession is a
-- credit decision. It goes to the roles that already carry delegated authority.
INSERT INTO auth.t_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM auth.t_role r
CROSS JOIN auth.t_permission p
WHERE p.code = 'product.price' AND r.code IN ('ADMIN', 'HOCRM', 'CEO', 'MD');

-- ---------------------------------------------------------------------------
-- Seed: e-Loan, version 1.
--
-- The specification names e-Loan as the first product and gives these figures
-- as "initial product configuration values, not hard-coded business rules". They
-- are seeded for exactly that reason: they are where a bank starts, and every
-- one of them is changed by issuing version 2, not by editing this row.
-- ---------------------------------------------------------------------------
INSERT INTO product.t_loan_product (code, name, name_bn, product_type, category, description)
VALUES ('ELOAN', 'e-Loan', 'ই-লোন', 'TERM_LOAN', 'PERSONAL',
        'Unsecured personal term loan, originated digitally and disbursed to an existing account');

INSERT INTO product.t_loan_product_version (
    product_id, version_no, status, effective_from,
    customer_segment, secured, currency,
    min_amount, max_amount, min_tenure_months, max_tenure_months,
    interest_method, interest_rate, grace_period_days, repayment_frequency,
    collateral_required, guarantor_required,
    income_multiple, max_dbr, regulatory_max_amount, recommended_ratio)
SELECT p.id, 1, 'ACTIVE', CURRENT_DATE,
       'ANY', FALSE, 'BDT',
       5000.0000, 50000.0000, 3, 12,
       'REDUCING_BALANCE', 9.000000, 0, 'MONTHLY',
       FALSE, FALSE,
       10.0000, 0.5000, 50000.0000, 0.7000
FROM product.t_loan_product p WHERE p.code = 'ELOAN';

INSERT INTO product.t_product_tenure (product_version_id, tenure_months)
SELECT v.id, t.months
FROM product.t_loan_product_version v
JOIN product.t_loan_product p ON p.id = v.product_id
CROSS JOIN (VALUES (3), (6), (9), (12)) AS t(months)
WHERE p.code = 'ELOAN' AND v.version_no = 1;

INSERT INTO product.t_product_fee (
    product_version_id, fee_code, name, calculation_method, flat_amount, rate, vat_rate, collected_at)
SELECT v.id, f.code, f.name, f.method, f.flat, f.rate, f.vat, f.collected
FROM product.t_loan_product_version v
JOIN product.t_loan_product p ON p.id = v.product_id
CROSS JOIN (VALUES
    ('PROCESSING', 'Processing fee', 'PERCENT_OF_PRINCIPAL', NULL::NUMERIC, 1.000000, 15.000000, 'DISBURSEMENT'),
    ('INSURANCE',  'Credit life insurance', 'PERCENT_OF_PRINCIPAL', NULL::NUMERIC, 0.500000, 0.000000, 'DISBURSEMENT'),
    ('LATE_PAYMENT', 'Late payment charge', 'FLAT', 500.0000, NULL::NUMERIC, 0.000000, 'ON_DEFAULT')
) AS f(code, name, method, flat, rate, vat, collected)
WHERE p.code = 'ELOAN' AND v.version_no = 1;

INSERT INTO product.t_product_risk_limit (product_version_id, risk_profile, max_amount)
SELECT v.id, r.grade, r.cap
FROM product.t_loan_product_version v
JOIN product.t_loan_product p ON p.id = v.product_id
CROSS JOIN (VALUES ('LOW', 50000.0000), ('MEDIUM', 35000.0000), ('HIGH', 15000.0000))
    AS r(grade, cap)
WHERE p.code = 'ELOAN' AND v.version_no = 1;
