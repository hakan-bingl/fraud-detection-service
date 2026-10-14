package io.softpay.fraud.rule;

import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.domain.RuleEvaluationResult;
import io.softpay.fraud.domain.TransactionContext;
import io.softpay.fraud.repository.TransactionStateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Rule 3: Velocity checks per terminal dynamically scaled by terminal threat score.
 */
@Component
@Order(3)
@RequiredArgsConstructor
public class TerminalVelocityRule implements FraudRule {

    private final FraudRulesProperties properties;
    private final TransactionStateRepository transactionStateRepository;

    @Override
    public String getName() {
        return "TerminalVelocityRule";
    }

    @Override
    public RuleEvaluationResult evaluate(TransactionContext context) {
        FraudRulesProperties.TerminalVelocityProperties props = properties.terminalVelocityRule();
        int threatScore = context.request().terminalThreatScore();
        String terminalId = context.request().terminalId();

        // MaxTxInWindow = max(minAllowedTx, round(baseMaxTxPerWindow * (1 - (threatScore / 100) * threatDeductionFactor)))
        double reduction = (threatScore / 100.0) * props.threatDeductionFactor();
        long allowedCount = Math.max(
                props.minAllowedTx(),
                Math.round(props.baseMaxTxPerWindow() * (1.0 - reduction))
        );

        Instant windowStart = context.evaluationTime().minus(props.windowSeconds(), ChronoUnit.SECONDS);
        int currentCount = transactionStateRepository.getTerminalTransactionCount(
                terminalId,
                windowStart,
                context.evaluationTime()
        );

        if (currentCount >= allowedCount) {
            return RuleEvaluationResult.reject(
                    getName(),
                    props.score(),
                    "Terminal velocity limit exceeded relative to threat score (" +
                            currentCount + " transactions in " + props.windowSeconds() +
                            "s window, max allowed: " + allowedCount + ")"
            );
        }

        return RuleEvaluationResult.passed(getName());
    }
}
