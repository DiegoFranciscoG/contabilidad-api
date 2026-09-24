package io.github.diegofranciscog.accounting.repository;

import io.github.diegofranciscog.accounting.domain.CostCenter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CostCenterRepository extends JpaRepository<CostCenter, Long> {

    Optional<CostCenter> findByCode(String code);

    boolean existsByCode(String code);

    List<CostCenter> findAllByOrderByCode();
}
