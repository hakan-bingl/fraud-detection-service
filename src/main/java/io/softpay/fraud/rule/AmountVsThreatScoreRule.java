package io.softpay.fraud.rule;

import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.records.data.TransactionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Rule 2: Amount evaluation dynamically bounded by terminal threat score.
 * High threat score terminals have strictly reduced transaction limits.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class AmountVsThreatScoreRule implements FraudRule {

    private final FraudRulesProperties properties;

    @Override
    public String getName() {
        return "AmountVsThreatScoreRule";
    }

    @Override
    public RuleEvaluationResult evaluate(TransactionContext context) {
        FraudRulesProperties.AmountRuleProperties props = properties.amountRule();
        int threatScore = context.request().terminalThreatScore();
        BigDecimal amount = context.amount();
        String currency = context.request().currency();

        // MaxAllowedAmount = baseMaxAmount * (1 - (threatScore / 100) * threatDeductionFactor)
        double threatRatio = (threatScore / 100.0) * props.threatDeductionFactor();
        double multiplier = Math.max(0.01, 1.0 - threatRatio);
        BigDecimal maxAllowedAmount = props.baseMaxAmountEur()
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(2, RoundingMode.HALF_UP);

        if (amount.compareTo(maxAllowedAmount) > 0) {
            double excess = amount.subtract(maxAllowedAmount).doubleValue();
            double ratio = excess / Math.max(1.0, maxAllowedAmount.doubleValue());
            int score = (int) Math.min(100, Math.round(70 + Math.min(1.0, ratio) * 30));

            return RuleEvaluationResult.reject(
                    getName(),
                    score,
                    "Amount exceeds allowed threshold for terminal threat score (amount: " +
                            amount + " " + currency + ", limit: " + maxAllowedAmount + " " + currency + ")"
            );
        }

        BigDecimal warningThreshold = maxAllowedAmount
                .multiply(BigDecimal.valueOf(props.warningThresholdRatio()))
                .setScale(2, RoundingMode.HALF_UP);

        if (amount.compareTo(warningThreshold) >= 0) {
            double range = maxAllowedAmount.subtract(warningThreshold).doubleValue();
            double diff = amount.subtract(warningThreshold).doubleValue();
            int score = (range > 0) ? (int) Math.min(50, Math.round(25 + (diff / range) * 25)) : 25;

            return RuleEvaluationResult.warning(
                    getName(),
                    score,
                    "Amount is approaching threshold for terminal threat score"
            );
        }

        return RuleEvaluationResult.passed(getName());
    }
}
