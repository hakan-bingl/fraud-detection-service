package io.softpay.fraud.rule;

import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.records.data.TransactionContext;

public interface FraudRule {
    String getName();
    RuleEvaluationResult evaluate(TransactionContext context);
}
