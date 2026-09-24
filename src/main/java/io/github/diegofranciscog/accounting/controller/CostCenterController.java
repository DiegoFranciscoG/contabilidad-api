package io.github.diegofranciscog.accounting.controller;

import io.github.diegofranciscog.accounting.dto.CostCenterResponse;
import io.github.diegofranciscog.accounting.dto.CreateCostCenterRequest;
import io.github.diegofranciscog.accounting.dto.UpdateCostCenterRequest;
import io.github.diegofranciscog.accounting.service.CostCenterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cost-centers")
@Tag(name = "Centros de costo")
public class CostCenterController {

    private final CostCenterService service;

    public CostCenterController(CostCenterService service) {
        this.service = service;
    }

    @GetMapping
    public List<CostCenterResponse> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public CostCenterResponse create(@Valid @RequestBody CreateCostCenterRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
    public CostCenterResponse update(@PathVariable @Pattern(regexp = "^[A-Z0-9-]{1,20}$") String code,
                                     @Valid @RequestBody UpdateCostCenterRequest request) {
        return service.update(code, request);
    }
}
