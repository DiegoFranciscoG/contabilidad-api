package io.github.diegofranciscog.accounting.service;

import io.github.diegofranciscog.accounting.domain.Account;
import io.github.diegofranciscog.accounting.domain.Nature;
import io.github.diegofranciscog.accounting.dto.AccountResponse;
import io.github.diegofranciscog.accounting.dto.CreateAccountRequest;
import io.github.diegofranciscog.accounting.dto.UpdateAccountRequest;
import io.github.diegofranciscog.accounting.exception.ConflictException;
import io.github.diegofranciscog.accounting.exception.DomainException;
import io.github.diegofranciscog.accounting.exception.NotFoundException;
import io.github.diegofranciscog.accounting.mapper.AccountingMapper;
import io.github.diegofranciscog.accounting.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class AccountService {

    private final AccountRepository accounts;
    private final AccountingMapper mapper;

    public AccountService(AccountRepository accounts, AccountingMapper mapper) {
        this.accounts = accounts;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list() {
        return accounts.findAllWithParentOrderByCode().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(String code) {
        return mapper.toResponse(require(code));
    }

    @Transactional
    public AccountResponse create(CreateAccountRequest request) {
        Account account = accounts.save(build(request.code(), request.name(), request.parentCode(),
                request.nature(), request.acceptsEntries()));
        return mapper.toResponse(account);
    }

    @Transactional
    public AccountResponse update(String code, UpdateAccountRequest request) {
        Account account = require(code);
        if (request.name() != null) {
            account.rename(request.name());
        }
        if (request.active() != null) {
            account.changeActive(request.active());
        }
        return mapper.toResponse(account);
    }

    /** Builds a new account, inferring the parent from the longest existing code prefix when none is given. */
    Account build(String code, String name, String parentCode, Nature nature,
                  boolean acceptsEntries) {
        if (accounts.existsByCode(code)) {
            throw new ConflictException("Ya existe la cuenta " + code);
        }
        Account parent = null;
        if (parentCode != null && !parentCode.isBlank()) {
            parent = require(parentCode);
        } else if (code.length() > 1) {
            parent = findClosestParent(code)
                    .orElseThrow(() -> new DomainException("No existe una cuenta padre para el código " + code));
        }
        return Account.create(code, name, parent, nature, acceptsEntries);
    }

    Account require(String code) {
        return accounts.findByCode(code).orElseThrow(() -> new NotFoundException("Cuenta", code));
    }

    private Optional<Account> findClosestParent(String code) {
        for (int length = code.length() - 1; length >= 1; length--) {
            Optional<Account> candidate = accounts.findByCode(code.substring(0, length));
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }
}
