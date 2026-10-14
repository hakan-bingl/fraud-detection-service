package io.softpay.fraud.domain;

import io.softpay.fraud.dto.TransactionEvaluationRequest;

import java.math.BigDecimal;
import java.time.Instant;

public record TransactionContext(
    TransactionEvaluationRequest request,
    BigDecimal amountEur,
    String hashedCardNumber,
    Instant evaluationTime
) {}
