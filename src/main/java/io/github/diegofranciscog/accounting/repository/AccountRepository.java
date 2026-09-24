package io.github.diegofranciscog.accounting.repository;

import io.github.diegofranciscog.accounting.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByParentId(Long parentId);

    @Query("select a from Account a left join fetch a.parent order by a.code")
    List<Account> findAllWithParentOrderByCode();
}
