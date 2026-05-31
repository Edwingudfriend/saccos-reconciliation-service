package com.reimagineafrica.reconciliation.repository;

import com.reimagineafrica.reconciliation.entity.UnmatchedPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnmatchedPaymentRepository extends JpaRepository<UnmatchedPayment, UUID> {

    Optional<UnmatchedPayment> findByGatewayReference(String gatewayReference);

    boolean existsByGatewayReference(String gatewayReference);

    Page<UnmatchedPayment> findBySaccoCodeAndResolvedFalseOrderByCreatedAtDesc(String saccoCode, Pageable pageable);

    List<UnmatchedPayment> findByResolvedFalseAndRetryCountLessThan(int maxRetries);

    long countBySaccoCodeAndResolvedFalse(String saccoCode);
}
