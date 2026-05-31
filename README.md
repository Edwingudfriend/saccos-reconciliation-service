# SACCOS Reconciliation Service

Reconciles mobile money payments (M-Pesa, Tigo Pesa, Airtel Money) against Apache Fineract transactions. Flags discrepancies for treasurer review and handles unmatched payments.

## What it does

```
Mobile Money Callback (stored in mobile-money-service)
        ↓
Fetch transactions for date + provider
        ↓
Fetch Fineract transactions for same date
        ↓
Match using 3-strategy cascade:
  1. externalReference → Fineract receiptNumber
  2. gatewayReference  → Fineract receiptNumber
  3. gatewayReference  → Fineract paymentReceiptNumber
        ↓
Classify each pair:
  MATCHED              — all good
  MISSING_IN_FINERACT  — money received, not posted → flag for manual posting
  MISSING_IN_GATEWAY   — Fineract has it, gateway doesn't → investigate
  AMOUNT_MISMATCH      — references match, amounts differ → flag
  DUPLICATE            — same payment posted twice → flag for reversal
        ↓
Unmatched payments (no member found for phone number)
  → Stored in unmatched_payments table
  → Treasurer resolves manually
        ↓
RabbitMQ event → notification-service sends summary to treasurer
```

## Schedule

Runs automatically at **00:30 AM EAT** for all active SACCOs — reconciles the previous day.

## API Endpoints

| Method | Path | Role | Action |
|--------|------|------|--------|
| POST | `/api/reconciliation/run` | TREASURER/ADMIN | Manual trigger for one provider |
| POST | `/api/reconciliation/run-all/{saccoCode}` | TREASURER/ADMIN | Run all providers |
| GET | `/api/reconciliation/summary/{saccoCode}` | TREASURER/MANAGER | Dashboard summary |
| GET | `/api/reconciliation/discrepancies/{saccoCode}` | TREASURER | All unresolved discrepancies |
| POST | `/api/reconciliation/discrepancies/{id}/resolve` | TREASURER | Resolve a discrepancy |
| GET | `/api/reconciliation/unmatched/{saccoCode}` | TREASURER | Unmatched payments |
| POST | `/api/reconciliation/unmatched/{id}/resolve` | TREASURER | Resolve unmatched payment |

## Resolution actions

| Action | Meaning |
|--------|---------|
| `POST_TO_FINERACT` | Manually post the missing payment to Fineract |
| `MARK_RESOLVED` | Mark as resolved with notes |
| `IGNORE` | Not a real discrepancy — ignore |
| `REQUEST_REFUND` | Initiate refund to member |
