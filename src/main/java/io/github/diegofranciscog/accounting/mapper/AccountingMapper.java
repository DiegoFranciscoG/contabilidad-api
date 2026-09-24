package io.github.diegofranciscog.accounting.mapper;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.AppUser;
import io.github.diegofranciscog.accounting.domain.CostCenter;
import io.github.diegofranciscog.accounting.domain.EventAccountMapping;
import io.github.diegofranciscog.accounting.domain.FiscalPeriod;
import io.github.diegofranciscog.accounting.dto.AccountResponse;
import io.github.diegofranciscog.accounting.dto.CostCenterResponse;
import io.github.diegofranciscog.accounting.dto.EventAccountMappingResponse;
import io.github.diegofranciscog.accounting.dto.FiscalPeriodResponse;
import io.github.diegofranciscog.accounting.dto.UserResponse;
import org.springframework.stereotype.Component;

/** Entity → DTO conversion for the master data of the ledger. */
@Component
public class AccountingMapper {

    public AccountResponse toResponse(Account account) {
        Account parent = account.getParent();
        return new AccountResponse(account.getId(), account.getCode(), account.getName(),
                parent == null ? null : parent.getCode(), account.getLevel(), account.getElement(),
                account.getNature(), account.isAcceptsEntries(), account.isActive());
    }

    public CostCenterResponse toResponse(CostCenter costCenter) {
        return new CostCenterResponse(costCenter.getId(), costCenter.getCode(), costCenter.getName(), costCenter.isActive());
    }

    public FiscalPeriodResponse toResponse(FiscalPeriod period) {
        return new FiscalPeriodResponse(period.getId(), period.getYear(), period.getMonth(), period.getStartDate(),
                period.getEndDate(), period.getStatus(), period.getClosedAt(), period.getClosedBy());
    }

    public UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isEnabled(),
                user.getCreatedAt());
    }

    public EventAccountMappingResponse toResponse(EventAccountMapping mapping) {
        return new EventAccountMappingResponse(mapping.getId(), mapping.getEventType(), mapping.getAccountRole(),
                mapping.getAccount().getCode(), mapping.getAccount().getName());
    }
}
