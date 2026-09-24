package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.domain.EventType;
import io.github.diegofranciscog.accounting.dto.EventAccountMappingResponse;
import io.github.diegofranciscog.accounting.dto.UpdateEventAccountMappingRequest;
import io.github.diegofranciscog.accounting.service.integration.EventAccountMappingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/event-account-mappings")
@Tag(name = "Integraciones (asientos automáticos)")
@PreAuthorize("hasRole('ADMIN')")
public class EventAccountMappingController {

    private final EventAccountMappingService service;

    public EventAccountMappingController(EventAccountMappingService service) {
        this.service = service;
    }

    @GetMapping
    public List<EventAccountMappingResponse> list() {
        return service.list();
    }

    @PutMapping("/{eventType}/{accountRole}")
    public EventAccountMappingResponse update(@PathVariable EventType eventType,
                                              @PathVariable @Pattern(regexp = "^[A-Z_]{1,40}$") String accountRole,
                                              @Valid @RequestBody UpdateEventAccountMappingRequest request) {
        return service.update(eventType, accountRole, request.accountCode());
    }
}
