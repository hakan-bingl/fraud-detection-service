package io.softpay.fraud;

import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.records.data.TransactionContext;
import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;
import io.softpay.fraud.model.records.data.Terminal;
import io.softpay.fraud.port.out.TerminalDataOut;
import io.softpay.fraud.rule.UnknownTerminalRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UnknownTerminalRuleTest {

    private TerminalDataOut terminalDataOut;
    private UnknownTerminalRule rule;

    @BeforeEach
    void setUp() {
        terminalDataOut = mock(TerminalDataOut.class);
        when(terminalDataOut.findByTerminalId("TERM-001"))
                .thenReturn(Optional.of(new Terminal("TERM-001", true, "Standard POS Terminal 1")));
        when(terminalDataOut.findByTerminalId("TERM-SUSPENDED"))
                .thenReturn(Optional.of(new Terminal("TERM-SUSPENDED", false, "Suspended Terminal")));
        when(terminalDataOut.findByTerminalId("NON-EXISTENT-TERM"))
                .thenReturn(Optional.empty());

        rule = new UnknownTerminalRule(terminalDataOut);
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
