package io.softpay.fraud;

import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.domain.RuleEvaluationResult;
import io.softpay.fraud.domain.TransactionContext;
import io.softpay.fraud.dto.TransactionEvaluationRequest;
import io.softpay.fraud.repository.InMemorySlidingWindowRepository;
import io.softpay.fraud.repository.TransactionStateRepository;
import io.softpay.fraud.rule.TerminalVelocityRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TerminalVelocityRuleTest {

    private TransactionStateRepository stateRepository;
    private TerminalVelocityRule rule;

    @BeforeEach
    void setUp() {
        stateRepository = new InMemorySlidingWindowRepository();
        FraudRulesProperties properties = new FraudRulesProperties(
                70,
                "test-salt",
                new FraudRulesProperties.AmountRuleProperties(new BigDecimal("5000.00"), 0.90, 0.70),
                new FraudRulesProperties.TerminalVelocityProperties(60, 10, 0.80, 2, 85),
                new FraudRulesProperties.CardVelocityProperties(60, 3, 90)
        );
        rule = new TerminalVelocityRule(properties, stateRepository);
    }

    @Test
    @DisplayName("Should permit transactions below threat-adjusted velocity limit and reject once breached")
    void testTerminalVelocityBreachAndReset() {
        String terminalId = "TERM-001";
        // Threat score 50: baseMax 10 * (1 - 0.40) = 6 allowed transactions
        int threatScore = 50;
        Instant t0 = Instant.parse("2026-10-14T12:00:00Z");

        // Simulate 5 previous transactions in the last 20 seconds
        for (int i = 0; i < 5; i++) {
            stateRepository.recordTransaction(terminalId, "card-1", t0.plusSeconds(i * 3));
        }

        // 6th transaction evaluated at t0 + 20s: existing count = 5 (< 6 allowed) -> passes
        TransactionContext context6 = createContext(terminalId, threatScore, t0.plusSeconds(20));
        RuleEvaluationResult result6 = rule.evaluate(context6);
        assertThat(result6.hardReject()).isFalse();
        assertThat(result6.scoreContribution()).isZero();

        // Record the 6th transaction
        stateRepository.recordTransaction(terminalId, "card-1", t0.plusSeconds(20));

        // 7th transaction evaluated at t0 + 25s: existing count = 6 (>= 6 allowed) -> rejected!
        TransactionContext context7 = createContext(terminalId, threatScore, t0.plusSeconds(25));
        RuleEvaluationResult result7 = rule.evaluate(context7);
        assertThat(result7.hardReject()).isTrue();
        assertThat(result7.scoreContribution()).isEqualTo(85);
        assertThat(result7.reason()).contains("Terminal velocity limit exceeded");

        // Advance clock past the 60-second window (t0 + 90s)
        Instant tFuture = t0.plusSeconds(90);
        TransactionContext futureContext = createContext(terminalId, threatScore, tFuture);
        RuleEvaluationResult futureResult = rule.evaluate(futureContext);
        assertThat(futureResult.hardReject()).isFalse();
        assertThat(futureResult.scoreContribution()).isZero();
    }

    private TransactionContext createContext(String terminalId, int threatScore, Instant time) {
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("50.00"),
                "EUR",
                terminalId,
                threatScore,
                "4532015112831111"
        );
        return new TransactionContext(request, new BigDecimal("50.00"), "hashed-pan", time);
    }
}
