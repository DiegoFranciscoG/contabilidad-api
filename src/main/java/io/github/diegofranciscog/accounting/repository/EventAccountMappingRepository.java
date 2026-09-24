package io.github.diegofranciscog.accounting.repository;

import io.github.diegofranciscog.accounting.domain.EventAccountMapping;
import io.github.diegofranciscog.accounting.domain.EventType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventAccountMappingRepository extends JpaRepository<EventAccountMapping, Long> {

    @EntityGraph(attributePaths = "account")
    List<EventAccountMapping> findByEventType(EventType eventType);

    @EntityGraph(attributePaths = "account")
    List<EventAccountMapping> findAllByOrderByEventTypeAscAccountRoleAsc();

    Optional<EventAccountMapping> findByEventTypeAndAccountRole(EventType eventType, String accountRole);
}
