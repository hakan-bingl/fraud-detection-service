package io.softpay.fraud.rule;

import io.softpay.fraud.domain.RuleEvaluationResult;
import io.softpay.fraud.domain.TransactionContext;

public interface FraudRule {
    String getName();
    RuleEvaluationResult evaluate(TransactionContext context);
}
