package com.reimagineafrica.reconciliation.controller;

import com.reimagineafrica.reconciliation.dto.request.ReconciliationRunRequest;
import com.reimagineafrica.reconciliation.dto.request.ResolveDiscrepancyRequest;
import com.reimagineafrica.reconciliation.dto.response.ReconciliationSummary;
import com.reimagineafrica.reconciliation.entity.ReconciliationRecord;
import com.reimagineafrica.reconciliation.entity.ReconciliationRun;
import com.reimagineafrica.reconciliation.entity.UnmatchedPayment;
import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import com.reimagineafrica.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    // ── Trigger runs ─────────────────────────────────────────────────

    @PostMapping("/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public ResponseEntity<ReconciliationRun> runReconciliation(
            @Valid @RequestBody ReconciliationRunRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(reconciliationService.runReconciliation(request, userId));
    }

    @PostMapping("/run-all/{saccoCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public ResponseEntity<Void> runAllProviders(
            @PathVariable String saccoCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader("X-User-Id") String userId) {
        reconciliationService.runAllProviders(saccoCode, date != null ? date : LocalDate.now().minusDays(1), userId);
        return ResponseEntity.ok().build();
    }

    // ── Summaries & dashboards ────────────────────────────────────────

    @GetMapping("/summary/{saccoCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER', 'MANAGER')")
    public ResponseEntity<ReconciliationSummary> getSummary(
            @PathVariable String saccoCode,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam PaymentProvider provider) {
        return ResponseEntity.ok(reconciliationService.getSummary(saccoCode, date, provider));
    }

    // ── Discrepancies ─────────────────────────────────────────────────

    @GetMapping("/discrepancies/{saccoCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public ResponseEntity<Page<ReconciliationRecord>> getDiscrepancies(
            @PathVariable String saccoCode,
            Pageable pageable) {
        return ResponseEntity.ok(reconciliationService.getDiscrepancies(saccoCode, pageable));
    }

    @PostMapping("/discrepancies/{recordId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public ResponseEntity<Void> resolveDiscrepancy(
            @PathVariable UUID recordId,
            @RequestBody ResolveDiscrepancyRequest request,
            @RequestHeader("X-User-Id") String userId) {
        reconciliationService.resolveDiscrepancy(recordId, request, userId);
        return ResponseEntity.ok().build();
    }

    // ── Unmatched payments ────────────────────────────────────────────

    @GetMapping("/unmatched/{saccoCode}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public ResponseEntity<Page<UnmatchedPayment>> getUnmatchedPayments(
            @PathVariable String saccoCode,
            Pageable pageable) {
        return ResponseEntity.ok(reconciliationService.getUnmatchedPayments(saccoCode, pageable));
    }

    @PostMapping("/unmatched/{unmatchedId}/resolve")
    @PreAuthorize("hasAnyRole('ADMIN', 'TREASURER')")
    public ResponseEntity<Void> resolveUnmatched(
            @PathVariable UUID unmatchedId,
            @RequestBody ResolveDiscrepancyRequest request,
            @RequestHeader("X-User-Id") String userId) {
        reconciliationService.resolveUnmatchedPayment(unmatchedId, request, userId);
        return ResponseEntity.ok().build();
    }
}
