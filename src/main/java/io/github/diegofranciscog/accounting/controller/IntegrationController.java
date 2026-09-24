package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.dto.IntegrationResult;
import io.github.diegofranciscog.accounting.dto.InvoiceIssuedEvent;
import io.github.diegofranciscog.accounting.dto.PayrollAccruedEvent;
import io.github.diegofranciscog.accounting.service.integration.IntegrationEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhooks for facturador-sri and nomina-ec. Requests are authenticated with an HMAC-SHA256 signature
 * (headers X-Timestamp and X-Signature), not with a user token. 201 = entry created, 200 = already processed.
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Tag(name = "Integraciones (asientos automáticos)")
@SecurityRequirement(name = "hmac")
public class IntegrationController {

    private final IntegrationEventService service;

    public IntegrationController(IntegrationEventService service) {
        this.service = service;
    }

    @PostMapping("/invoices")
    @Operation(summary = "Factura emitida (facturador-sri): CxC = ventas + IVA por pagar")
    public ResponseEntity<IntegrationResult> invoiceIssued(@Valid @RequestBody InvoiceIssuedEvent event) {
        return respond(service.invoiceIssued(event));
    }

    @PostMapping("/payrolls")
    @Operation(summary = "Nómina aprobada (nomina-ec): gastos de personal contra sueldos, IESS y beneficios por pagar")
    public ResponseEntity<IntegrationResult> payrollAccrued(@Valid @RequestBody PayrollAccruedEvent event) {
        return respond(service.payrollAccrued(event));
    }

    private static ResponseEntity<IntegrationResult> respond(IntegrationResult result) {
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }
}
