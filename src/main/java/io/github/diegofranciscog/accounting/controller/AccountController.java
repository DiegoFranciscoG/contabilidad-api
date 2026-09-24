package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.dto.AccountImportResult;
import io.github.diegofranciscog.accounting.dto.AccountResponse;
import io.github.diegofranciscog.accounting.dto.CreateAccountRequest;
import io.github.diegofranciscog.accounting.dto.UpdateAccountRequest;
import io.github.diegofranciscog.accounting.service.AccountImportService;
import io.github.diegofranciscog.accounting.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Plan de cuentas")
public class AccountController {

    private static final String CODE = "^[1-5][0-9]{0,19}$";

    private final AccountService service;
    private final AccountImportService importService;

    public AccountController(AccountService service, AccountImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    @GetMapping
    @Operation(summary = "Lista el plan de cuentas ordenado por código")
    public List<AccountResponse> list() {
        return service.list();
    }

    @GetMapping("/{code}")
    public AccountResponse get(@PathVariable @Pattern(regexp = CODE) String code) {
        return service.get(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Crea una cuenta; si no se envía parentCode se usa el prefijo más largo existente")
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Cambia el nombre o activa/desactiva una cuenta")
    public AccountResponse update(@PathVariable @Pattern(regexp = CODE) String code,
                                  @Valid @RequestBody UpdateAccountRequest request) {
        return service.update(code, request);
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Importa cuentas desde CSV (code,name,nature,accepts_entries); todo o nada")
    public AccountImportResult importCsv(@RequestPart("file") MultipartFile file) throws IOException {
        AccountImportService.validateFile(file.getOriginalFilename(), file.getContentType(), file.getSize());
        return importService.importCsv(file.getBytes());
    }
}
