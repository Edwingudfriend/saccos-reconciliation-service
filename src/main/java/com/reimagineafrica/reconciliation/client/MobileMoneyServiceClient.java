package com.reimagineafrica.reconciliation.client;

import com.reimagineafrica.reconciliation.dto.GatewayTransaction;
import com.reimagineafrica.reconciliation.enums.PaymentProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;

/**
 * Fetches confirmed payment callbacks from the mobile-money-service.
 * The mobile-money-service persists every M-Pesa/Tigo/Airtel callback —
 * we query it here instead of calling the telco APIs directly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MobileMoneyServiceClient {

    private final WebClient mobileMoneyWebClient;

    public List<GatewayTransaction> getTransactions(String saccoCode,
                                                     PaymentProvider provider,
                                                     LocalDate date) {
        log.info("Fetching {} transactions for SACCO {} on {}", provider, saccoCode, date);

        try {
            return mobileMoneyWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/transactions")
                            .queryParam("saccoCode", saccoCode)
                            .queryParam("provider", provider.name())
                            .queryParam("date", date.toString())
                            .queryParam("status", "SUCCESS")
                            .build())
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<List<GatewayTransaction>>() {})
                    .block();
        } catch (Exception e) {
            log.error("Failed to fetch {} transactions from mobile-money-service: {}", provider, e.getMessage());
            return List.of();
        }
    }
}
