package io.softpay.fraud.model.records.data;

import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionContext(
    TransactionEvaluationRequest request,
    BigDecimal amount,
    String hashedCardNumber,
    Instant evaluationTime
) {
    public BigDecimal amountEur() {
        return amount;
    }
}
