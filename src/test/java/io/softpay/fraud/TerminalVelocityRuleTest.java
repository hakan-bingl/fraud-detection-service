package io.softpay.fraud;

import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.records.data.TransactionContext;
import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;
import io.softpay.fraud.persistence.repository.TransactionStateRepository;
import io.softpay.fraud.rule.TerminalVelocityRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TerminalVelocityRuleTest {

    private TransactionStateRepository stateRepository;
    private TerminalVelocityRule rule;

    @BeforeEach
    void setUp() {
        stateRepository = mock(TransactionStateRepository.class);
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
        int threatScore = 50; // max allowed = 6 tx
        Instant t0 = Instant.parse("2026-10-14T12:00:00Z");

        // When 5 transactions in window (< 6 allowed) -> passes
        when(stateRepository.getTerminalTransactionCount(eq(terminalId), any(), any()))
                .thenReturn(5);

        TransactionContext context6 = createContext(terminalId, threatScore, t0.plusSeconds(20));
        RuleEvaluationResult result6 = rule.evaluate(context6);
        assertThat(result6.hardReject()).isFalse();
        assertThat(result6.scoreContribution()).isZero();

        // When 6 transactions in window (>= 6 allowed) -> rejected!
        when(stateRepository.getTerminalTransactionCount(eq(terminalId), any(), any()))
                .thenReturn(6);

        TransactionContext context7 = createContext(terminalId, threatScore, t0.plusSeconds(25));
        RuleEvaluationResult result7 = rule.evaluate(context7);
        assertThat(result7.hardReject()).isTrue();
        assertThat(result7.scoreContribution()).isEqualTo(85);
        assertThat(result7.reason()).contains("Terminal velocity limit exceeded");

        // When window resets (0 count) -> passes cleanly
        when(stateRepository.getTerminalTransactionCount(eq(terminalId), any(), any()))
                .thenReturn(0);

        TransactionContext futureContext = createContext(terminalId, threatScore, t0.plusSeconds(90));
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
