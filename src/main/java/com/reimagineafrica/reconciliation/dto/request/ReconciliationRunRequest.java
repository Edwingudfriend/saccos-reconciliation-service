package com.reimagineafrica.reconciliation.dto.request;

import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReconciliationRunRequest {
    @NotNull private String saccoCode;
    @NotNull private PaymentProvider provider;
    private LocalDate date; // defaults to today if null
}
