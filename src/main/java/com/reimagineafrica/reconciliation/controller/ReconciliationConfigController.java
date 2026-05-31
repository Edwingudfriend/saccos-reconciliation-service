package com.reimagineafrica.reconciliation.controller;

import com.reimagineafrica.reconciliation.entity.ReconciliationConfig;
import com.reimagineafrica.reconciliation.service.ReconciliationConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reconciliation-configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationConfigController {

    private final ReconciliationConfigService configService;

    @GetMapping
    public ResponseEntity<List<ReconciliationConfig>> getAll() {
        return ResponseEntity.ok(configService.getAllActive());
    }

    @GetMapping("/{saccoCode}")
    public ResponseEntity<ReconciliationConfig> getByCode(@PathVariable String saccoCode) {
        return ResponseEntity.ok(configService.getByCode(saccoCode));
    }

    @GetMapping("/{saccoCode}/resolve")
    public ResponseEntity<ReconciliationConfig> resolve(@PathVariable String saccoCode) {
        return ResponseEntity.ok(configService.resolveConfig(saccoCode));
    }

    @PostMapping
    public ResponseEntity<ReconciliationConfig> create(@RequestBody ReconciliationConfig config) {
        return ResponseEntity.status(HttpStatus.CREATED).body(configService.create(config));
    }

    @PutMapping("/{saccoCode}")
    public ResponseEntity<ReconciliationConfig> update(
            @PathVariable String saccoCode,
            @RequestBody ReconciliationConfig config,
            @RequestHeader("X-User-Id") String updatedBy) {
        return ResponseEntity.ok(configService.update(saccoCode, config, updatedBy));
    }

    @DeleteMapping("/{saccoCode}/deactivate")
    public ResponseEntity<Void> deactivate(
            @PathVariable String saccoCode,
            @RequestHeader("X-User-Id") String updatedBy) {
        configService.deactivate(saccoCode, updatedBy);
        return ResponseEntity.noContent().build();
    }
}
