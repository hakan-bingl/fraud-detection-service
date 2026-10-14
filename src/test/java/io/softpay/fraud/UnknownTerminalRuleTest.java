package io.softpay.fraud;

import io.softpay.fraud.domain.RuleEvaluationResult;
import io.softpay.fraud.domain.TransactionContext;
import io.softpay.fraud.dto.TransactionEvaluationRequest;
import io.softpay.fraud.repository.InMemoryTerminalRepository;
import io.softpay.fraud.repository.Terminal;
import io.softpay.fraud.repository.TerminalRepository;
import io.softpay.fraud.rule.UnknownTerminalRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UnknownTerminalRuleTest {

    private TerminalRepository terminalRepository;
    private UnknownTerminalRule rule;

    @BeforeEach
    void setUp() {
        terminalRepository = new InMemoryTerminalRepository();
        rule = new UnknownTerminalRule(terminalRepository);
    }

    @Test
    @DisplayName("Active known terminal should pass with 0 score")
    void testKnownActiveTerminalPasses() {
        TransactionContext context = createContextForTerminal("TERM-001");
        RuleEvaluationResult result = rule.evaluate(context);

        assertThat(result.hardReject()).isFalse();
        assertThat(result.scoreContribution()).isZero();
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("Unknown terminal should trigger hard rejection with score 100")
    void testUnknownTerminalRejected() {
        TransactionContext context = createContextForTerminal("NON-EXISTENT-TERM");
        RuleEvaluationResult result = rule.evaluate(context);

        assertThat(result.hardReject()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(100);
        assertThat(result.reason()).contains("Unknown or unregistered terminal ID");
    }

    @Test
    @DisplayName("Suspended terminal should trigger hard rejection with score 100")
    void testSuspendedTerminalRejected() {
        TransactionContext context = createContextForTerminal("TERM-SUSPENDED");
        RuleEvaluationResult result = rule.evaluate(context);

        assertThat(result.hardReject()).isTrue();
        assertThat(result.scoreContribution()).isEqualTo(100);
        assertThat(result.reason()).contains("Unknown or unregistered terminal ID");
    }

    private TransactionContext createContextForTerminal(String terminalId) {
        TransactionEvaluationRequest req = new TransactionEvaluationRequest(
                new BigDecimal("100.00"),
                "EUR",
                terminalId,
                10,
                "4532015112831111"
        );
        return new TransactionContext(req, new BigDecimal("100.00"), "hashed-pan", Instant.now());
    }
}
