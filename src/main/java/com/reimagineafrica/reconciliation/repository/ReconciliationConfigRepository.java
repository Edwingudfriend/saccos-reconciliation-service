package com.reimagineafrica.reconciliation.repository;

import com.reimagineafrica.reconciliation.entity.ReconciliationConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReconciliationConfigRepository extends JpaRepository<ReconciliationConfig, Long> {

    Optional<ReconciliationConfig> findBySaccoCode(String saccoCode);

    Optional<ReconciliationConfig> findBySaccoCodeAndActiveTrue(String saccoCode);

    List<ReconciliationConfig> findByActiveTrue();

    boolean existsBySaccoCode(String saccoCode);
}
