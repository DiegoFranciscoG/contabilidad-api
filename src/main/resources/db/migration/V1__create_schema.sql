-- Core schema for the double-entry accounting engine.
-- Rule references (Rn) point to docs/investigacion.md.

CREATE TABLE app_users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT ck_app_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_app_users_role CHECK (role IN ('ADMIN', 'ACCOUNTANT', 'VIEWER'))
);

-- Chart of accounts (plan de cuentas). R3, R4, R5.
CREATE TABLE accounts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    parent_id       BIGINT REFERENCES accounts (id),
    level           SMALLINT     NOT NULL,
    element         VARCHAR(10)  NOT NULL,
    nature          VARCHAR(6)   NOT NULL,
    accepts_entries BOOLEAN      NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_accounts_code UNIQUE (code),
    CONSTRAINT ck_accounts_code CHECK (code ~ '^[1-5][0-9]*$'),
    CONSTRAINT ck_accounts_level CHECK (level BETWEEN 1 AND 10),
    CONSTRAINT ck_accounts_root CHECK ((parent_id IS NULL) = (level = 1)),
    CONSTRAINT ck_accounts_element CHECK (element IN ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE')),
    CONSTRAINT ck_accounts_nature CHECK (nature IN ('DEBIT', 'CREDIT'))
);
CREATE INDEX ix_accounts_parent ON accounts (parent_id);

CREATE TABLE cost_centers (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code       VARCHAR(20)  NOT NULL,
    name       VARCHAR(120) NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_cost_centers_code UNIQUE (code),
    CONSTRAINT ck_cost_centers_code CHECK (code ~ '^[A-Z0-9-]+$')
);

-- Monthly fiscal periods (S4). R6.
CREATE TABLE fiscal_periods (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    year       SMALLINT    NOT NULL,
    month      SMALLINT    NOT NULL,
    start_date DATE        NOT NULL,
    end_date   DATE        NOT NULL,
    status     VARCHAR(6)  NOT NULL DEFAULT 'OPEN',
    closed_at  TIMESTAMPTZ,
    closed_by  VARCHAR(254),
    CONSTRAINT uk_fiscal_periods_year_month UNIQUE (year, month),
    CONSTRAINT ck_fiscal_periods_year CHECK (year BETWEEN 2000 AND 2100),
    CONSTRAINT ck_fiscal_periods_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_fiscal_periods_start CHECK (start_date = make_date(year, month, 1)),
    CONSTRAINT ck_fiscal_periods_end CHECK (end_date = (start_date + INTERVAL '1 month' - INTERVAL '1 day')::date),
    CONSTRAINT ck_fiscal_periods_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_fiscal_periods_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

-- Gap-free entry numbering per fiscal year, assigned when an entry is posted.
CREATE TABLE entry_sequences (
    fiscal_year SMALLINT PRIMARY KEY,
    last_number INT NOT NULL DEFAULT 0 CHECK (last_number >= 0)
);

-- Journal entries (asientos). R1, R6, R10.
CREATE TABLE journal_entries (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_number      INT,
    fiscal_year       SMALLINT      NOT NULL,
    entry_date        DATE          NOT NULL,
    period_id         BIGINT        NOT NULL REFERENCES fiscal_periods (id),
    description       VARCHAR(500)  NOT NULL,
    status            VARCHAR(8)    NOT NULL DEFAULT 'DRAFT',
    entry_type        VARCHAR(10)   NOT NULL DEFAULT 'STANDARD',
    source            VARCHAR(10)   NOT NULL DEFAULT 'MANUAL',
    source_reference  VARCHAR(100),
    reverses_entry_id BIGINT REFERENCES journal_entries (id),
    total_debit       NUMERIC(18, 2) NOT NULL,
    total_credit      NUMERIC(18, 2) NOT NULL,
    created_by        VARCHAR(254)  NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    posted_at         TIMESTAMPTZ,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_journal_entries_number UNIQUE (fiscal_year, entry_number),
    CONSTRAINT uk_journal_entries_source UNIQUE (source, source_reference),
    CONSTRAINT uk_journal_entries_reverses UNIQUE (reverses_entry_id),
    CONSTRAINT ck_journal_entries_balanced CHECK (total_debit = total_credit AND total_debit > 0),
    CONSTRAINT ck_journal_entries_status CHECK (status IN ('DRAFT', 'POSTED')),
    CONSTRAINT ck_journal_entries_type CHECK (entry_type IN ('STANDARD', 'OPENING', 'CLOSING', 'REVERSAL')),
    CONSTRAINT ck_journal_entries_source CHECK (source IN ('MANUAL', 'INVOICING', 'PAYROLL', 'SYSTEM')),
    CONSTRAINT ck_journal_entries_number CHECK ((status = 'POSTED') = (entry_number IS NOT NULL)),
    CONSTRAINT ck_journal_entries_posted_at CHECK ((status = 'POSTED') = (posted_at IS NOT NULL)),
    CONSTRAINT ck_journal_entries_reversal CHECK ((entry_type = 'REVERSAL') = (reverses_entry_id IS NOT NULL)),
    CONSTRAINT ck_journal_entries_year CHECK (fiscal_year = EXTRACT(YEAR FROM entry_date))
);
CREATE INDEX ix_journal_entries_period ON journal_entries (period_id);
CREATE INDEX ix_journal_entries_date ON journal_entries (entry_date, entry_number);

-- Journal lines (lineas_asiento). R2.
CREATE TABLE journal_lines (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entry_id       BIGINT         NOT NULL REFERENCES journal_entries (id) ON DELETE CASCADE,
    line_number    SMALLINT       NOT NULL,
    account_id     BIGINT         NOT NULL REFERENCES accounts (id),
    cost_center_id BIGINT REFERENCES cost_centers (id),
    debit          NUMERIC(18, 2) NOT NULL DEFAULT 0,
    credit         NUMERIC(18, 2) NOT NULL DEFAULT 0,
    description    VARCHAR(250),
    -- Deferred so a draft can replace its lines in one flush (Hibernate inserts before it deletes).
    CONSTRAINT uk_journal_lines_number UNIQUE (entry_id, line_number) DEFERRABLE INITIALLY DEFERRED,
    CONSTRAINT ck_journal_lines_non_negative CHECK (debit >= 0 AND credit >= 0),
    CONSTRAINT ck_journal_lines_one_side CHECK ((debit > 0) <> (credit > 0))
);
CREATE INDEX ix_journal_lines_account ON journal_lines (account_id);
CREATE INDEX ix_journal_lines_cost_center ON journal_lines (cost_center_id);

-- Accounts used by automatic entries coming from other systems (S6).
CREATE TABLE event_account_mappings (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type   VARCHAR(30) NOT NULL,
    account_role VARCHAR(40) NOT NULL,
    account_id   BIGINT      NOT NULL REFERENCES accounts (id),
    CONSTRAINT uk_event_account_mappings UNIQUE (event_type, account_role),
    CONSTRAINT ck_event_account_mappings_type CHECK (event_type IN ('INVOICE_ISSUED', 'PAYROLL_ACCRUED'))
);
