package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.dto.report.ReportFormat;
import io.github.diegofranciscog.accounting.mapper.ReportTabularMapper;
import io.github.diegofranciscog.accounting.service.ReportService;
import io.github.diegofranciscog.accounting.service.export.ExcelExporter;
import io.github.diegofranciscog.accounting.service.export.PdfExporter;
import io.github.diegofranciscog.accounting.service.export.TabularReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.function.Function;
import java.util.function.Supplier;

/** Every report answers JSON by default, or a downloadable file with {@code format=XLSX|PDF}. */
@RestController
@RequestMapping("/api/v1/reports")
@Tag(name = "Reportes")
public class ReportController {

    static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ReportService reports;
    private final ReportTabularMapper tabular;
    private final ExcelExporter excel;
    private final PdfExporter pdf;

    public ReportController(ReportService reports, ReportTabularMapper tabular, ExcelExporter excel, PdfExporter pdf) {
        this.reports = reports;
        this.tabular = tabular;
        this.excel = excel;
        this.pdf = pdf;
    }

    @GetMapping("/journal")
    @Operation(summary = "Libro diario (máximo 366 días)")
    public ResponseEntity<?> journal(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(defaultValue = "JSON") ReportFormat format) {
        return respond(format, () -> reports.journal(from, to), tabular::journal);
    }

    @GetMapping("/ledger")
    @Operation(summary = "Libro mayor de una cuenta (una cuenta de grupo incluye sus subcuentas)")
    public ResponseEntity<?> ledger(@RequestParam @Pattern(regexp = "^[1-5][0-9]{0,19}$") String account,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(defaultValue = "JSON") ReportFormat format) {
        return respond(format, () -> reports.ledger(account, from, to), tabular::ledger);
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "Balance de comprobación de sumas y saldos")
    public ResponseEntity<?> trialBalance(@RequestParam @Min(2000) @Max(2100) int year,
                                          @RequestParam(defaultValue = "1") @Min(1) @Max(12) int fromMonth,
                                          @RequestParam(defaultValue = "12") @Min(1) @Max(12) int toMonth,
                                          @RequestParam(defaultValue = "JSON") ReportFormat format) {
        return respond(format, () -> reports.trialBalance(year, fromMonth, toMonth), tabular::trialBalance);
    }

    @GetMapping("/balance-sheet")
    @Operation(summary = "Estado de situación financiera al cierre del mes")
    public ResponseEntity<?> balanceSheet(@RequestParam @Min(2000) @Max(2100) int year,
                                          @RequestParam @Min(1) @Max(12) int month,
                                          @RequestParam(defaultValue = "3") @Min(2) @Max(10) int level,
                                          @RequestParam(defaultValue = "JSON") ReportFormat format) {
        return respond(format, () -> reports.balanceSheet(year, month, level), tabular::balanceSheet);
    }

    @GetMapping("/income-statement")
    @Operation(summary = "Estado de resultados por función (excluye el asiento de cierre); filtro opcional por centro de costo")
    public ResponseEntity<?> incomeStatement(@RequestParam @Min(2000) @Max(2100) int year,
                                             @RequestParam(defaultValue = "1") @Min(1) @Max(12) int fromMonth,
                                             @RequestParam(defaultValue = "12") @Min(1) @Max(12) int toMonth,
                                             @RequestParam(required = false) @Pattern(regexp = "^[A-Z0-9-]{1,20}$") String costCenter,
                                             @RequestParam(defaultValue = "3") @Min(2) @Max(10) int level,
                                             @RequestParam(defaultValue = "JSON") ReportFormat format) {
        return respond(format, () -> reports.incomeStatement(year, fromMonth, toMonth, costCenter, level),
                tabular::incomeStatement);
    }

    private <T> ResponseEntity<?> respond(ReportFormat format, Supplier<T> report, Function<T, TabularReport> toTable) {
        T data = report.get();
        if (format == ReportFormat.JSON) {
            return ResponseEntity.ok(data);
        }
        TabularReport table = toTable.apply(data);
        return format == ReportFormat.XLSX
                ? file(excel.export(table), table.fileName() + ".xlsx", XLSX)
                : file(pdf.export(table), table.fileName() + ".pdf", MediaType.APPLICATION_PDF);
    }

    private static ResponseEntity<byte[]> file(byte[] content, String fileName, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(content);
    }
}
