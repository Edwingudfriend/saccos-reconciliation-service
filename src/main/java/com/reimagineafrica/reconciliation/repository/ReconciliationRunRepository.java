package com.reimagineafrica.reconciliation.repository;

import com.reimagineafrica.reconciliation.entity.ReconciliationRun;
import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, UUID> {

    Optional<ReconciliationRun> findBySaccoCodeAndProviderAndReconciliationDate(
            String saccoCode, PaymentProvider provider, LocalDate date);

    List<ReconciliationRun> findBySaccoCodeOrderByReconciliationDateDesc(String saccoCode);

    List<ReconciliationRun> findByReconciliationDateAndRunStatus(LocalDate date, String runStatus);
}
