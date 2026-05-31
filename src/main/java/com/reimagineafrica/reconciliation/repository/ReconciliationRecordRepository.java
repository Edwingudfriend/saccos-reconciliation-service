package com.reimagineafrica.reconciliation.repository;

import com.reimagineafrica.reconciliation.entity.ReconciliationRecord;
import com.reimagineafrica.reconciliation.enums.ReconciliationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, UUID> {

    Page<ReconciliationRecord> findByReconciliationRunId(UUID runId, Pageable pageable);

    Page<ReconciliationRecord> findByReconciliationRunIdAndStatus(UUID runId, ReconciliationStatus status, Pageable pageable);

    Optional<ReconciliationRecord> findByGatewayReference(String gatewayReference);

    // All unresolved discrepancies for a SACCO
    @Query("""
        SELECT r FROM ReconciliationRecord r
        JOIN r.reconciliationRun run
        WHERE run.saccoCode = :saccoCode
        AND r.status NOT IN ('MATCHED', 'RESOLVED', 'IGNORED')
        ORDER BY r.createdAt DESC
    """)
    Page<ReconciliationRecord> findUnresolvedBySacco(String saccoCode, Pageable pageable);

    @Query("SELECT COUNT(r) FROM ReconciliationRecord r JOIN r.reconciliationRun run WHERE run.saccoCode = :saccoCode AND r.status NOT IN ('MATCHED', 'RESOLVED', 'IGNORED')")
    long countUnresolvedBySacco(String saccoCode);
}
