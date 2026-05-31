package com.reimagineafrica.reconciliation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberLookupService {

    private final WebClient memberWebClient;

    /**
     * Queries member-service to determine why a payment couldn't be matched.
     * Returns a human-readable reason code.
     */
    public String findUnmatchedReason(String phone, BigDecimal amount) {
        try {
            Map response = memberWebClient.get()
                    .uri("/api/members/by-phone/" + phone + "/loan-summary")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null) {
                return "PHONE_NOT_FOUND";
            }

            Boolean hasActiveLoan = (Boolean) response.get("hasActiveLoan");
            Integer activeLoanCount = (Integer) response.get("activeLoanCount");
            Boolean hasSavings = (Boolean) response.get("hasSavingsAccount");

            if (Boolean.FALSE.equals(hasActiveLoan) && Boolean.FALSE.equals(hasSavings)) {
                return "NO_ACTIVE_ACCOUNT";
            }
            if (activeLoanCount != null && activeLoanCount > 1) {
                return "MULTIPLE_ACTIVE_LOANS";
            }
            return "WRONG_REFERENCE";

        } catch (Exception e) {
            log.warn("Could not determine unmatched reason for phone {}: {}", phone, e.getMessage());
            return "PHONE_NOT_FOUND";
        }
    }
}
