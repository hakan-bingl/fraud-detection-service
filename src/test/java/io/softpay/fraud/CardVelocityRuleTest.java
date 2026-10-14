package io.softpay.fraud;

import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.records.data.TransactionContext;
import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;
import io.softpay.fraud.persistence.repository.TransactionStateRepository;
import io.softpay.fraud.rule.CardVelocityRule;
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

class CardVelocityRuleTest {

    private TransactionStateRepository stateRepository;
    private CardVelocityRule rule;

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
        rule = new CardVelocityRule(properties, stateRepository);
    }

    @Test
    @DisplayName("Should reject card usage frequency exceeding 3 transactions within 60 seconds")
    void testCardVelocityLimit() {
        String hashedCard = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        Instant t0 = Instant.parse("2026-10-14T12:00:00Z");

        // 3rd transaction: count = 2 (< 3) -> passes
        when(stateRepository.getCardTransactionCount(eq(hashedCard), any(), any()))
                .thenReturn(2);

        TransactionContext ctx3 = createContext(hashedCard, t0.plusSeconds(15));
        RuleEvaluationResult res3 = rule.evaluate(ctx3);
        assertThat(res3.hardReject()).isFalse();
        assertThat(res3.scoreContribution()).isZero();

        // 4th transaction: count = 3 (>= 3) -> hard rejection
        when(stateRepository.getCardTransactionCount(eq(hashedCard), any(), any()))
                .thenReturn(3);

        TransactionContext ctx4 = createContext(hashedCard, t0.plusSeconds(20));
        RuleEvaluationResult res4 = rule.evaluate(ctx4);
        assertThat(res4.hardReject()).isTrue();
        assertThat(res4.scoreContribution()).isEqualTo(90);
        assertThat(res4.reason()).contains("Suspicious card usage frequency");

        // After window expiry: count = 0 -> passes cleanly
        when(stateRepository.getCardTransactionCount(eq(hashedCard), any(), any()))
                .thenReturn(0);

        TransactionContext ctxFuture = createContext(hashedCard, t0.plusSeconds(80));
        RuleEvaluationResult resFuture = rule.evaluate(ctxFuture);
        assertThat(resFuture.hardReject()).isFalse();
        assertThat(resFuture.scoreContribution()).isZero();
    }

    private TransactionContext createContext(String hashedCard, Instant time) {
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                new BigDecimal("75.00"),
                "EUR",
                "TERM-001",
                10,
                "4532015112831111"
        );
        return new TransactionContext(request, new BigDecimal("75.00"), hashedCard, time);
    }
}
