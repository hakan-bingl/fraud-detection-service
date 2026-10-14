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
 * Rule 4: Card usage frequency (velocity) within a rolling time window.
 */
@Component
@Order(4)
@RequiredArgsConstructor
public class CardVelocityRule implements FraudRule {

    private final FraudRulesProperties properties;
    private final TransactionStateRepository transactionStateRepository;

    @Override
    public String getName() {
        return "CardVelocityRule";
    }

    @Override
    public RuleEvaluationResult evaluate(TransactionContext context) {
        FraudRulesProperties.CardVelocityProperties props = properties.cardVelocityRule();
        String hashedCard = context.hashedCardNumber();

        Instant windowStart = context.evaluationTime().minus(props.windowSeconds(), ChronoUnit.SECONDS);
        int currentCount = transactionStateRepository.getCardTransactionCount(
                hashedCard,
                windowStart,
                context.evaluationTime()
        );

        if (currentCount >= props.maxTxPerWindow()) {
            return RuleEvaluationResult.reject(
                    getName(),
                    props.score(),
                    "Suspicious card usage frequency detected within time window"
            );
        }

        return RuleEvaluationResult.passed(getName());
    }
}
