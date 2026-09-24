package io.github.diegofranciscog.accounting.mapper;

import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.JournalLineResponse;
import io.github.diegofranciscog.accounting.dto.report.BalanceSheetReport;
import io.github.diegofranciscog.accounting.dto.report.IncomeStatementReport;
import io.github.diegofranciscog.accounting.dto.report.JournalReport;
import io.github.diegofranciscog.accounting.dto.report.LedgerReport;
import io.github.diegofranciscog.accounting.dto.report.StatementLine;
import io.github.diegofranciscog.accounting.dto.report.TrialBalanceReport;
import io.github.diegofranciscog.accounting.service.export.TabularReport;
import io.github.diegofranciscog.accounting.service.export.TabularReport.Column;
import io.github.diegofranciscog.accounting.service.export.TabularReport.Row;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Converts each report into the format-neutral table used by the Excel and PDF exporters. */
@Component
public class ReportTabularMapper {

    private static final String CURRENCY = "Valores en USD";

    public TabularReport journal(JournalReport report) {
        List<Row> rows = new ArrayList<>();
        for (JournalEntryResponse entry : report.entries()) {
            rows.add(row(true, entry.date().toString(), entry.number(), entry.description(), "", "", null, null));
            for (JournalLineResponse line : entry.lines()) {
                rows.add(row(false, "", "", line.description() == null ? "" : line.description(), line.accountCode(),
                        line.accountName(), line.debit(), line.credit()));
            }
        }
        rows.add(row(true, "", "", "TOTALES", "", "", report.totalDebit(), report.totalCredit()));
        return new TabularReport("Libro diario", "Del " + report.from() + " al " + report.to() + " · " + CURRENCY,
                "libro-diario-" + report.from() + "_" + report.to(),
                List.of(text("Fecha", 11), text("Asiento", 10), text("Glosa / detalle", 40), text("Cuenta", 12),
                        text("Nombre", 36), number("Debe"), number("Haber")),
                rows);
    }

    public TabularReport ledger(LedgerReport report) {
        List<Row> rows = new ArrayList<>();
        rows.add(row(true, "", "", "", "SALDO INICIAL", null, null, report.openingBalance()));
        for (LedgerReport.Line line : report.lines()) {
            rows.add(row(false, line.date().toString(), line.entryNumber(), line.accountCode(), line.description(),
                    line.debit(), line.credit(), line.balance()));
        }
        rows.add(row(true, "", "", "", "TOTALES Y SALDO FINAL", report.totalDebit(), report.totalCredit(), report.closingBalance()));
        return new TabularReport("Libro mayor · " + report.accountCode() + " " + report.accountName(),
                "Del " + report.from() + " al " + report.to() + " · naturaleza " + report.nature() + " · " + CURRENCY,
                "libro-mayor-" + report.accountCode() + "-" + report.from() + "_" + report.to(),
                List.of(text("Fecha", 11), text("Asiento", 10), text("Cuenta", 12), text("Detalle", 44),
                        number("Debe"), number("Haber"), number("Saldo")),
                rows);
    }

    public TabularReport trialBalance(TrialBalanceReport report) {
        List<Row> rows = new ArrayList<>();
        for (TrialBalanceReport.Row row : report.rows()) {
            rows.add(row(!row.detail(), row.code(), indent(row.level() - 1) + row.name(), row.openingDebit(),
                    row.openingCredit(), row.debit(), row.credit(), row.debitBalance(), row.creditBalance()));
        }
        rows.add(row(true, "", "TOTALES (cuentas de detalle)", report.totalOpeningDebit(), report.totalOpeningCredit(),
                report.totalDebit(), report.totalCredit(), report.totalDebitBalance(), report.totalCreditBalance()));
        return new TabularReport("Balance de comprobación",
                report.year() + " · meses " + report.fromMonth() + " a " + report.toMonth() + " · "
                        + (report.balanced() ? "cuadrado" : "DESCUADRADO") + " · " + CURRENCY,
                "balance-comprobacion-" + report.year() + "-" + report.fromMonth() + "_" + report.toMonth(),
                List.of(text("Código", 12), text("Cuenta", 42), number("Saldo inicial deudor"),
                        number("Saldo inicial acreedor"), number("Debe"), number("Haber"), number("Saldo deudor"),
                        number("Saldo acreedor")),
                rows);
    }

    public TabularReport balanceSheet(BalanceSheetReport report) {
        return statement("Estado de situación financiera",
                "Al " + report.asOf() + " · " + (report.balanced() ? "Activo = Pasivo + Patrimonio" : "DESCUADRADO")
                        + " · " + CURRENCY,
                "estado-situacion-" + report.asOf(), report.lines());
    }

    public TabularReport incomeStatement(IncomeStatementReport report) {
        String costCenter = report.costCenterCode() == null ? "" : " · centro de costo " + report.costCenterCode();
        return statement("Estado de resultados",
                report.year() + " · meses " + report.fromMonth() + " a " + report.toMonth() + costCenter + " · " + CURRENCY,
                "estado-resultados-" + report.year() + "-" + report.fromMonth() + "_" + report.toMonth(), report.lines());
    }

    private static TabularReport statement(String title, String subtitle, String fileName, List<StatementLine> lines) {
        List<Row> rows = lines.stream()
                .map(line -> row(line.kind() != StatementLine.Kind.DETAIL, line.code() == null ? "" : line.code(),
                        indent(line.indent()) + line.label(), line.amount()))
                .toList();
        return new TabularReport(title, subtitle, fileName,
                List.of(text("Código", 12), text("Concepto", 60), number("Valor")), rows);
    }

    private static String indent(int level) {
        return "  ".repeat(Math.max(0, level));
    }

    private static Row row(boolean emphasis, Object... cells) {
        return new Row(Arrays.asList(cells), emphasis);
    }

    private static Column text(String header, int width) {
        return new Column(header, false, width);
    }

    private static Column number(String header) {
        return new Column(header, true, 16);
    }
}
