package com.reimagineafrica.reconciliation.event;

import com.reimagineafrica.reconciliation.entity.ReconciliationRun;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private static final String EXCHANGE = "saccos.reconciliation.events";

    public void publishDiscrepanciesFound(ReconciliationRun run) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, "reconciliation.discrepancies.found", Map.of(
                    "saccoCode", run.getSaccoCode(),
                    "provider", run.getProvider().name(),
                    "date", run.getReconciliationDate().toString(),
                    "unresolvedCount", String.valueOf(run.getUnresolvedCount()),
                    "missingInFineract", String.valueOf(run.getMissingInFineractCount()),
                    "amountMismatch", String.valueOf(run.getAmountMismatchCount())
            ));
        } catch (Exception e) {
            log.error("Failed to publish reconciliation event: {}", e.getMessage());
        }
    }
}
