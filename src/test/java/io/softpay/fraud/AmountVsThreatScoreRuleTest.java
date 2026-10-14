package io.softpay.fraud;

import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.records.data.TransactionContext;
import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;
import io.softpay.fraud.rule.AmountVsThreatScoreRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AmountVsThreatScoreRuleTest {

    private AmountVsThreatScoreRule rule;

    @BeforeEach
    void setUp() {
        FraudRulesProperties properties = new FraudRulesProperties(
                70,
                "test-salt",
                new FraudRulesProperties.AmountRuleProperties(new BigDecimal("5000.00"), 0.90, 0.70),
                new FraudRulesProperties.TerminalVelocityProperties(60, 10, 0.80, 2, 85),
                new FraudRulesProperties.CardVelocityProperties(60, 3, 90)
        );
        rule = new AmountVsThreatScoreRule(properties);
    }

    @ParameterizedTest
    @CsvSource({
            // amount, threatScore, shouldReject, minScore, maxScore
            "1000.00,   0,   false,  0,   0",    // Low amount, lowest threat (limit 5000, warning 3500)
            "4000.00,   0,   false,  25,  50",   // Warning range (between 3500 and 5000)
            "6000.00,   0,   true,   70,  100",  // Exceeds 5000 limit -> reject
            "1000.00,   50,  false,  0,   0",    // Low amount, threat 50 (limit 2750, warning 1925)
            "2000.00,   50,  false,  25,  50",   // Warning range (between 1925 and 2750)
            "3000.00,   50,  true,   70,  100",  // Exceeds 2750 limit -> reject
            "900.00,    90,  false,  25,  50",   // Approaching 950 limit (warning 665)
            "1000.00,   90,  true,   70,  100",  // Exceeds 950 limit -> reject
            "400.00,    100, false,  25,  50",   // Approaching 500 limit (warning 350)
            "600.00,    100, true,   70,  100"   // Exceeds 500 limit -> reject
    })
    @DisplayName("Should evaluate amount vs threat score correctly across boundaries")
    void testAmountVsThreatScoreParametrized(
            String amountStr,
            int threatScore,
            boolean expectedReject,
            int minScore,
            int maxScore
    ) {
        BigDecimal amount = new BigDecimal(amountStr);
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                amount,
                "EUR",
                "TERM-001",
                threatScore,
                "4532015112831111"
        );
        TransactionContext context = new TransactionContext(request, amount, "hashed", Instant.now());

        RuleEvaluationResult result = rule.evaluate(context);

        assertThat(result.hardReject()).isEqualTo(expectedReject);
        assertThat(result.scoreContribution())
                .isBetween(minScore, maxScore);
        if (expectedReject) {
            assertThat(result.reason()).contains("Amount exceeds allowed threshold");
        }
    }

    @Test
    @DisplayName("Should evaluate amounts in transaction currency directly")
    void testDirectCurrencyEvaluation() {
        BigDecimal amount = new BigDecimal("6000.00");
        TransactionEvaluationRequest request = new TransactionEvaluationRequest(
                amount,
                "DKK",
                "TERM-001",
                10,
                "4532015112831111"
        );
        TransactionContext context = new TransactionContext(request, amount, "hashed", Instant.now());
        RuleEvaluationResult result = rule.evaluate(context);

        // limit with threat=10 is 5000 * (1 - 0.09) = 4550. 6000 > 4550 -> reject
        assertThat(result.hardReject()).isTrue();
        assertThat(result.scoreContribution()).isGreaterThanOrEqualTo(70);
        assertThat(result.reason()).contains("DKK");
    }
}
