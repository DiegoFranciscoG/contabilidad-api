-- Default accounts for automatic entries from facturador-sri (INVOICE_ISSUED) and nomina-ec (PAYROLL_ACCRUED).
-- They can be changed later through the API without touching code.
INSERT INTO event_account_mappings (event_type, account_role, account_id)
SELECT m.event_type, m.account_role, a.id
FROM (VALUES ('INVOICE_ISSUED', 'RECEIVABLE', '101020502'),
             ('INVOICE_ISSUED', 'SALES_REVENUE', '4101'),
             ('INVOICE_ISSUED', 'SERVICE_REVENUE', '4102'),
             ('INVOICE_ISSUED', 'VAT_PAYABLE', '201070101'),
             ('PAYROLL_ACCRUED', 'SALARY_EXPENSE', '520201'),
             ('PAYROLL_ACCRUED', 'EMPLOYER_CONTRIBUTION_EXPENSE', '520202'),
             ('PAYROLL_ACCRUED', 'BENEFITS_EXPENSE', '520203'),
             ('PAYROLL_ACCRUED', 'SALARIES_PAYABLE', '201070401'),
             ('PAYROLL_ACCRUED', 'IESS_PAYABLE', '2010703'),
             ('PAYROLL_ACCRUED', 'BENEFITS_PAYABLE', '201070402')) AS m (event_type, account_role, code)
         JOIN accounts a ON a.code = m.code;
