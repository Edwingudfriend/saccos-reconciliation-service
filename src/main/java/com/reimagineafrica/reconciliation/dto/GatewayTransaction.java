package com.reimagineafrica.reconciliation.dto;

import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Normalised transaction from any payment gateway.
 * All providers (M-Pesa, Tigo, Airtel, Snippe) map to this.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GatewayTransaction {
    private String gatewayReference;    // Unique ID from the gateway
    private String externalReference;   // Reference set by the SACCO (e.g. loan ref)
    private PaymentProvider provider;
    private BigDecimal amount;
    private String currency;
    private String senderPhone;
    private String senderName;
    private String status;              // SUCCESS, FAILED, PENDING
    private LocalDateTime timestamp;
    private String transactionType;     // C2B (collection), B2C (disbursement)
}
