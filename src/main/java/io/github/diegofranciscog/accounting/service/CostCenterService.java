package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.domain.CostCenter;
import io.github.diegofranciscog.accounting.dto.CostCenterResponse;
import io.github.diegofranciscog.accounting.dto.CreateCostCenterRequest;
import io.github.diegofranciscog.accounting.dto.UpdateCostCenterRequest;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import io.github.diegofranciscog.accounting.mapper.AccountingMapper;
import io.github.diegofranciscog.accounting.repository.CostCenterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CostCenterService {

    private final CostCenterRepository costCenters;
    private final AccountingMapper mapper;

    public CostCenterService(CostCenterRepository costCenters, AccountingMapper mapper) {
        this.costCenters = costCenters;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<CostCenterResponse> list() {
        return costCenters.findAllByOrderByCode().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public CostCenterResponse create(CreateCostCenterRequest request) {
        if (costCenters.existsByCode(request.code())) {
            throw new ConflictException("Ya existe el centro de costo " + request.code());
        }
        return mapper.toResponse(costCenters.save(new CostCenter(request.code(), request.name())));
    }

    @Transactional
    public CostCenterResponse update(String code, UpdateCostCenterRequest request) {
        CostCenter costCenter = require(code);
        if (request.name() != null) {
            costCenter.rename(request.name());
        }
        if (request.active() != null) {
            costCenter.changeActive(request.active());
        }
        return mapper.toResponse(costCenter);
    }

    CostCenter require(String code) {
        return costCenters.findByCode(code).orElseThrow(() -> new NotFoundException("Centro de costo", code));
    }
}
