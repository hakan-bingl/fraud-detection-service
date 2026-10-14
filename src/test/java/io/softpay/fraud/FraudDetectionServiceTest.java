package io.softpay.fraud;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.softpay.fraud.adapter.in.FraudDetectionService;
import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.enums.RejectionStatus;
import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;
import io.softpay.fraud.model.records.response.TransactionEvaluationResponse;
import io.softpay.fraud.persistence.repository.TransactionStateRepository;
import io.softpay.fraud.rule.FraudRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FraudDetectionServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private TransactionStateRepository stateRepository;
    private FraudRulesProperties properties;
    private Clock clock;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        stateRepository = mock(TransactionStateRepository.class);
        properties = new FraudRulesProperties(
                70,
                "test-salt-123",
                new FraudRulesProperties.AmountRuleProperties(new BigDecimal("5000.00"), 0.90, 0.70),
                new FraudRulesProperties.TerminalVelocityProperties(60, 10, 0.80, 2, 85),
                new FraudRulesProperties.CardVelocityProperties(60, 3, 90)
        );
        clock = Clock.fixed(Instant.parse("2026-10-14T12:00:00Z"), ZoneOffset.UTC);
    }

    @Test
    @DisplayName("Should approve transaction when all rules pass")
    void testTransactionApprovedWhenAllRulesPass() {
        FraudRule passingRule = mock(FraudRule.class);
        when(passingRule.getName()).thenReturn("PassingRule");
        when(passingRule.evaluate(any())).thenReturn(RuleEvaluationResult.passed("PassingRule"));

        FraudDetectionService service = new FraudDetectionService(
                List.of(passingRule),
                stateRepository,
                properties,
                meterRegistry,
                clock
        );

        TransactionEvaluationRequest req = new TransactionEvaluationRequest(
                new BigDecimal("100.00"),
                "EUR",
                "TERM-001",
                10,
                "4532015112831111"
        );

        TransactionEvaluationResponse response = service.evaluate(req);

        assertThat(response.rejectionStatus()).isEqualTo(RejectionStatus.ALLOWED);
        assertThat(response.rejectionMessage()).isEqualTo("Transaction approved");
        assertThat(response.fraudScore()).isZero();
        assertThat(response.isRejected()).isFalse();

        assertThat(meterRegistry.counter("fraud.evaluations.total", "status", "ALLOWED").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should aggregate scores from multiple rules and reject when threshold is met")
    void testMultipleRulesScoreAggregationAndRejection() {
        FraudRule rule1 = mock(FraudRule.class);
        when(rule1.getName()).thenReturn("Rule1");
        when(rule1.evaluate(any())).thenReturn(RuleEvaluationResult.warning("Rule1", 40, "Warning 1"));

        FraudRule rule2 = mock(FraudRule.class);
        when(rule2.getName()).thenReturn("Rule2");
        when(rule2.evaluate(any())).thenReturn(RuleEvaluationResult.warning("Rule2", 45, "Warning 2"));

        FraudDetectionService service = new FraudDetectionService(
                List.of(rule1, rule2),
                stateRepository,
                properties,
                meterRegistry,
                clock
        );

        TransactionEvaluationRequest req = new TransactionEvaluationRequest(
                new BigDecimal("2000.00"),
                "EUR",
                "TERM-001",
                20,
                "4532015112831111"
        );

        TransactionEvaluationResponse response = service.evaluate(req);

        assertThat(response.rejectionStatus()).isEqualTo(RejectionStatus.REJECTED);
        assertThat(response.fraudScore()).isEqualTo(85);
        assertThat(response.rejectionMessage()).isEqualTo("Warning 1; Warning 2");
        assertThat(response.isRejected()).isTrue();

        assertThat(meterRegistry.counter("fraud.evaluations.total", "status", "REJECTED").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should cap score at 100 when accumulated score exceeds 100")
    void testScoreCappingAt100() {
        FraudRule rule1 = mock(FraudRule.class);
        when(rule1.getName()).thenReturn("Rule1");
        when(rule1.evaluate(any())).thenReturn(RuleEvaluationResult.warning("Rule1", 60, "Warning 1"));

        FraudRule rule2 = mock(FraudRule.class);
        when(rule2.getName()).thenReturn("Rule2");
        when(rule2.evaluate(any())).thenReturn(RuleEvaluationResult.warning("Rule2", 70, "Warning 2"));

        FraudDetectionService service = new FraudDetectionService(
                List.of(rule1, rule2),
                stateRepository,
                properties,
                meterRegistry,
                clock
        );

        TransactionEvaluationRequest req = new TransactionEvaluationRequest(
                new BigDecimal("100.00"),
                "EUR",
                "TERM-001",
                10,
                "4532015112831111"
        );

        TransactionEvaluationResponse response = service.evaluate(req);

        assertThat(response.fraudScore()).isEqualTo(100);
        assertThat(response.rejectionStatus()).isEqualTo(RejectionStatus.REJECTED);
    }
}
