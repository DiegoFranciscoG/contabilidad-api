package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.domain.EntrySource;
import io.github.diegofranciscog.accounting.domain.EntryStatus;
import io.github.diegofranciscog.accounting.dto.JournalEntryRequest;
import io.github.diegofranciscog.accounting.dto.JournalEntryResponse;
import io.github.diegofranciscog.accounting.dto.JournalEntrySummary;
import io.github.diegofranciscog.accounting.dto.ReverseEntryRequest;
import io.github.diegofranciscog.accounting.service.JournalEntryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/journal-entries")
@Tag(name = "Asientos")
public class JournalEntryController {

    private final JournalEntryService service;

    public JournalEntryController(JournalEntryService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Busca asientos por estado, origen y rango de fechas (paginado)")
    public Page<JournalEntrySummary> search(
            @RequestParam(required = false) EntryStatus status,
            @RequestParam(required = false) EntrySource source,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = {"entryDate", "id"}, direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(status, source, from, to, pageable);
    }

    @GetMapping("/{id}")
    public JournalEntryResponse get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Crea un asiento balanceado (borrador o contabilizado con post=true)")
    public JournalEntryResponse create(@Valid @RequestBody JournalEntryRequest request, Principal principal) {
        return service.create(request, principal.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Reemplaza un borrador; un asiento contabilizado no se edita")
    public JournalEntryResponse update(@PathVariable long id, @Valid @RequestBody JournalEntryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public void delete(@PathVariable long id) {
        service.deleteDraft(id);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Contabiliza un borrador y le asigna el siguiente número del año")
    public JournalEntryResponse post(@PathVariable long id) {
        return service.post(id);
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    @Operation(summary = "Anula un asiento contabilizado con su asiento espejo en un periodo abierto")
    public JournalEntryResponse reverse(@PathVariable long id, @Valid @RequestBody(required = false) ReverseEntryRequest request,
                                        Principal principal) {
        return service.reverse(id, request, principal.getName());
    }
}
