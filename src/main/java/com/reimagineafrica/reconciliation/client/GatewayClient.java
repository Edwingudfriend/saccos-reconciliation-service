package com.reimagineafrica.reconciliation.client;

import com.reimagineafrica.reconciliation.dto.GatewayTransaction;

import java.time.LocalDate;
import java.util.List;

/**
 * Common interface for all payment gateway clients.
 * Each provider implements this — reconciliation engine works with the interface.
 */
public interface GatewayClient {
    List<GatewayTransaction> fetchTransactions(String saccoCode, LocalDate date);
    String getProviderName();
}
