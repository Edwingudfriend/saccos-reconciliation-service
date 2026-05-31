package com.reimagineafrica.reconciliation.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ResolveDiscrepancyRequest {
    @NotBlank private String action;
    // Actions: POST_TO_FINERACT, IGNORE, MARK_DUPLICATE, REQUEST_REFUND
    private String notes;
    private Long fineractLoanId;        // needed for POST_TO_FINERACT
    private Long fineractSavingsId;     // needed for POST_TO_FINERACT (savings)
}
