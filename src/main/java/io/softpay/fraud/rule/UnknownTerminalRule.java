package io.softpay.fraud.rule;

import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.records.data.TransactionContext;
import io.softpay.fraud.model.records.data.Terminal;
import io.softpay.fraud.port.out.TerminalDataOut;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 1: Unknown or inactive terminal verification.
 * Automatically rejects transactions originating from unprovisioned or suspended terminals.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class UnknownTerminalRule implements FraudRule {

    private final TerminalDataOut terminalDataOut;

    @Override
    public String getName() {
        return "UnknownTerminalRule";
    }

    @Override
    public RuleEvaluationResult evaluate(TransactionContext context) {
        String terminalId = context.request().terminalId();
        Optional<Terminal> terminal = terminalDataOut.findByTerminalId(terminalId);

        if (terminal.isEmpty() || !terminal.get().active()) {
            return RuleEvaluationResult.reject(
                    getName(),
                    100,
                    "Unknown or unregistered terminal ID"
            );
        }

        return RuleEvaluationResult.passed(getName());
    }
}
