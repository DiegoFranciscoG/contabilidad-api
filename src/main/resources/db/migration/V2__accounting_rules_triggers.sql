-- Accounting rules enforced by the database as the last line of defence.
-- Every message starts with "accounting_rule:" so the API can translate it into a 422 response.

-- R3, R4: account hierarchy.
CREATE FUNCTION fn_accounts_validate() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    parent           accounts%ROWTYPE;
    expected_element VARCHAR(10);
BEGIN
    expected_element := CASE substr(NEW.code, 1, 1)
                            WHEN '1' THEN 'ASSET'
                            WHEN '2' THEN 'LIABILITY'
                            WHEN '3' THEN 'EQUITY'
                            WHEN '4' THEN 'INCOME'
                            ELSE 'EXPENSE' END;
    IF NEW.element <> expected_element THEN
        RAISE EXCEPTION 'accounting_rule: account % must belong to element %', NEW.code, expected_element;
    END IF;

    IF TG_OP = 'UPDATE' AND (NEW.code <> OLD.code OR NEW.parent_id IS DISTINCT FROM OLD.parent_id) THEN
        RAISE EXCEPTION 'accounting_rule: code and parent of account % cannot change', OLD.code;
    END IF;

    IF NEW.parent_id IS NULL THEN
        IF length(NEW.code) <> 1 THEN
            RAISE EXCEPTION 'accounting_rule: root account % must have a one-digit code', NEW.code;
        END IF;
    ELSE
        SELECT * INTO parent FROM accounts WHERE id = NEW.parent_id;
        IF NOT (NEW.code LIKE parent.code || '%' AND length(NEW.code) > length(parent.code)) THEN
            RAISE EXCEPTION 'accounting_rule: code % must start with parent code %', NEW.code, parent.code;
        END IF;
        IF NEW.level <> parent.level + 1 THEN
            RAISE EXCEPTION 'accounting_rule: account % must be level %', NEW.code, parent.level + 1;
        END IF;
        IF parent.accepts_entries THEN
            RAISE EXCEPTION 'accounting_rule: parent account % accepts entries and cannot have children', parent.code;
        END IF;
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.accepts_entries AND NOT OLD.accepts_entries
        AND EXISTS (SELECT 1 FROM accounts WHERE parent_id = NEW.id) THEN
        RAISE EXCEPTION 'accounting_rule: account % has children and cannot accept entries', NEW.code;
    END IF;
    IF TG_OP = 'UPDATE' AND OLD.accepts_entries AND NOT NEW.accepts_entries
        AND EXISTS (SELECT 1 FROM journal_lines WHERE account_id = NEW.id) THEN
        RAISE EXCEPTION 'accounting_rule: account % already has movements', NEW.code;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounts_validate
    BEFORE INSERT OR UPDATE ON accounts
    FOR EACH ROW EXECUTE FUNCTION fn_accounts_validate();

-- R6: closed periods never reopen and cannot be closed with pending drafts.
CREATE FUNCTION fn_fiscal_periods_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status = 'CLOSED' THEN
            RAISE EXCEPTION 'accounting_rule: closed period %-% cannot be deleted', OLD.year, OLD.month;
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'accounting_rule: closed period %-% is immutable', OLD.year, OLD.month;
    END IF;
    IF NEW.status = 'CLOSED' AND EXISTS (SELECT 1 FROM journal_entries WHERE period_id = NEW.id AND status = 'DRAFT') THEN
        RAISE EXCEPTION 'accounting_rule: period %-% has draft entries', NEW.year, NEW.month;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_fiscal_periods_guard
    BEFORE UPDATE OR DELETE ON fiscal_periods
    FOR EACH ROW EXECUTE FUNCTION fn_fiscal_periods_guard();

-- R6: posted entries and entries of closed periods are immutable.
CREATE FUNCTION fn_journal_entries_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    period fiscal_periods%ROWTYPE;
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        IF OLD.status = 'POSTED' THEN
            RAISE EXCEPTION 'accounting_rule: posted entry %-% is immutable; reverse it instead', OLD.fiscal_year, OLD.entry_number;
        END IF;
        SELECT * INTO period FROM fiscal_periods WHERE id = OLD.period_id;
        IF period.status = 'CLOSED' THEN
            RAISE EXCEPTION 'accounting_rule: period %-% is closed', period.year, period.month;
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    SELECT * INTO period FROM fiscal_periods WHERE id = NEW.period_id;
    IF period.status = 'CLOSED' THEN
        RAISE EXCEPTION 'accounting_rule: period %-% is closed', period.year, period.month;
    END IF;
    IF NEW.entry_date NOT BETWEEN period.start_date AND period.end_date THEN
        RAISE EXCEPTION 'accounting_rule: entry date % is outside period %-%', NEW.entry_date, period.year, period.month;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_journal_entries_guard
    BEFORE INSERT OR UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION fn_journal_entries_guard();

-- R3, R6: lines only change while the entry is a draft, and only hit active detail accounts.
CREATE FUNCTION fn_journal_lines_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    entry_status VARCHAR(8);
    target       accounts%ROWTYPE;
BEGIN
    SELECT status INTO entry_status FROM journal_entries WHERE id = COALESCE(NEW.entry_id, OLD.entry_id);
    -- Lines deleted by ON DELETE CASCADE: the entry trigger already validated the delete.
    IF entry_status IS NULL THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    IF entry_status <> 'DRAFT' THEN
        RAISE EXCEPTION 'accounting_rule: lines of a posted entry are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;

    SELECT * INTO target FROM accounts WHERE id = NEW.account_id;
    IF NOT target.accepts_entries THEN
        RAISE EXCEPTION 'accounting_rule: account % is a group account and does not accept entries', target.code;
    END IF;
    IF NOT target.active THEN
        RAISE EXCEPTION 'accounting_rule: account % is inactive', target.code;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_journal_lines_guard
    BEFORE INSERT OR UPDATE OR DELETE ON journal_lines
    FOR EACH ROW EXECUTE FUNCTION fn_journal_lines_guard();

-- R1: debit = credit, checked at COMMIT so all lines of the entry are already written.
CREATE FUNCTION fn_journal_entry_balance_check() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    target_id    BIGINT;
    header       journal_entries%ROWTYPE;
    line_count   INT;
    debit_total  NUMERIC(18, 2);
    credit_total NUMERIC(18, 2);
BEGIN
    IF TG_TABLE_NAME = 'journal_entries' THEN
        target_id := NEW.id;
    ELSE
        target_id := COALESCE(NEW.entry_id, OLD.entry_id);
    END IF;

    SELECT * INTO header FROM journal_entries WHERE id = target_id;
    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT count(*), COALESCE(sum(debit), 0), COALESCE(sum(credit), 0)
    INTO line_count, debit_total, credit_total
    FROM journal_lines
    WHERE entry_id = target_id;

    IF line_count < 2 THEN
        RAISE EXCEPTION 'accounting_rule: entry % needs at least two lines', target_id;
    END IF;
    IF debit_total <> credit_total THEN
        RAISE EXCEPTION 'accounting_rule: entry % is unbalanced (debit %, credit %)', target_id, debit_total, credit_total;
    END IF;
    IF debit_total <> header.total_debit OR credit_total <> header.total_credit THEN
        RAISE EXCEPTION 'accounting_rule: entry % totals do not match its lines', target_id;
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_journal_lines_balance
    AFTER INSERT OR UPDATE OR DELETE ON journal_lines
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_journal_entry_balance_check();

CREATE CONSTRAINT TRIGGER trg_journal_entries_balance
    AFTER INSERT OR UPDATE ON journal_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION fn_journal_entry_balance_check();
