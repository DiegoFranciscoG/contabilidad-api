package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.dto.FiscalPeriodResponse;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.service.FiscalPeriodService;
import io.github.diegofranciscog.accounting.service.YearEndClosingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Periodos y cierre")
public class FiscalPeriodController {

    private final FiscalPeriodService service;
    private final YearEndClosingService closingService;

    public FiscalPeriodController(FiscalPeriodService service, YearEndClosingService closingService) {
        this.service = service;
        this.closingService = closingService;
    }

    @GetMapping("/fiscal-periods")
    public List<FiscalPeriodResponse> list(@RequestParam(required = false) @Min(2000) @Max(2100) Integer year) {
        return service.list(year);
    }

    @PostMapping("/fiscal-years/{year}/periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Abre el ejercicio: crea los 12 periodos mensuales")
    public List<FiscalPeriodResponse> openYear(@PathVariable @Min(2000) @Max(2100) int year) {
        return service.openYear(year);
    }

    @PostMapping("/fiscal-periods/{year}/{month}/close")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cierra un periodo (en orden y sin borradores); un periodo cerrado no se reabre")
    public FiscalPeriodResponse close(@PathVariable @Min(2000) @Max(2100) int year,
                                      @PathVariable @Min(1) @Max(12) int month, Principal principal) {
        return service.close(year, month, principal.getName());
    }

    @PostMapping("/fiscal-years/{year}/closing-entry")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Genera el asiento de cierre: salda ingresos y gastos contra 30701/30702")
    public JournalEntryResponse closingEntry(@PathVariable @Min(2000) @Max(2100) int year, Principal principal) {
        return closingService.close(year, principal.getName());
    }
}
