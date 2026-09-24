package io.github.diegofranciscog.accounting.service.integration;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.EventAccountMapping;
import io.github.diegofranciscog.accounting.domain.EventType;
import io.github.diegofranciscog.accounting.dto.EventAccountMappingResponse;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import io.github.diegofranciscog.accounting.mapper.AccountingMapper;
import io.github.diegofranciscog.accounting.repository.AccountRepository;
import io.github.diegofranciscog.accounting.repository.EventAccountMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lets an administrator point each amount of an automatic entry to a different account. */
@Service
public class EventAccountMappingService {

    private final EventAccountMappingRepository mappings;
    private final AccountRepository accounts;
    private final AccountingMapper mapper;

    public EventAccountMappingService(EventAccountMappingRepository mappings, AccountRepository accounts,
                                      AccountingMapper mapper) {
        this.mappings = mappings;
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<EventAccountMappingResponse> list() {
        return mappings.findAllByOrderByEventTypeAscAccountRoleAsc().stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public EventAccountMappingResponse update(EventType eventType, String accountRole, String accountCode) {
        EventAccountMapping mapping = mappings.findByEventTypeAndAccountRole(eventType, accountRole)
                .orElseThrow(() -> new NotFoundException("Mapeo", eventType + "/" + accountRole));
        Account account = accounts.findByCode(accountCode).orElseThrow(() -> new NotFoundException("Cuenta", accountCode));
        mapping.changeAccount(account);
        return mapper.toResponse(mapping);
    }
}
