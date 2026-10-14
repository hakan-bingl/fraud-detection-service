package io.softpay.fraud.adapter.in;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.model.enums.RejectionStatus;
import io.softpay.fraud.model.records.data.RuleEvaluationResult;
import io.softpay.fraud.model.records.data.TransactionContext;
import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;
import io.softpay.fraud.model.records.response.TransactionEvaluationResponse;
import io.softpay.fraud.persistence.repository.TransactionStateRepository;
import io.softpay.fraud.port.in.FraudDetectionIn;
import io.softpay.fraud.rule.FraudRule;
import io.softpay.fraud.utils.CardSecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Core orchestration engine for evaluating fraud rules and computing composite risk scores.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FraudDetectionService implements FraudDetectionIn {

    private final List<FraudRule> rules;
    private final TransactionStateRepository transactionStateRepository;
    private final FraudRulesProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    @Override
    public TransactionEvaluationResponse evaluate(TransactionEvaluationRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Instant now = clock.instant();

        try {
            String hashedPan = CardSecurityUtils.hashPan(request.cardNumber(), properties.salt());

            TransactionContext context = new TransactionContext(
                    request,
                    request.amount(),
                    hashedPan,
                    now
            );

            log.info("Evaluating transaction: terminalId={}, threatScore={}, amount={}, currency={}, maskedPan={}",
                    request.terminalId(), request.terminalThreatScore(), request.amount(), request.currency(), CardSecurityUtils.maskPan(request.cardNumber()));

            int totalScoreContribution = 0;
            boolean hasHardReject = false;
            List<String> reasons = new ArrayList<>();

            for (FraudRule rule : rules) {
                RuleEvaluationResult result = rule.evaluate(context);
                if (result.scoreContribution() > 0 || result.hardReject()) {
                    totalScoreContribution += result.scoreContribution();
                    if (result.reason() != null && !result.reason().isBlank()) {
                        reasons.add(result.reason());
                    }
                }
                if (result.hardReject()) {
                    hasHardReject = true;
                }
            }

            int finalScore = Math.min(100, totalScoreContribution);
            if (hasHardReject && finalScore < properties.rejectionThreshold()) {
                finalScore = Math.max(finalScore, properties.rejectionThreshold());
            }

            boolean isRejected = hasHardReject || (finalScore >= properties.rejectionThreshold());
            RejectionStatus status = isRejected ? RejectionStatus.REJECTED : RejectionStatus.ALLOWED;
            String message = isRejected
                    ? (reasons.isEmpty() ? "Transaction rejected due to fraud risk" : String.join("; ", reasons))
                    : "Transaction approved";

            // Record transaction for sliding-window velocity checks
            transactionStateRepository.recordTransaction(request.terminalId(), hashedPan, now);

            // Record metrics
            meterRegistry.counter("fraud.evaluations.total", "status", status.name()).increment();

            log.info("Evaluation outcome: status={}, score={}, reasons={}", status, finalScore, reasons);

            return new TransactionEvaluationResponse(status, message, finalScore);
        } finally {
            sample.stop(meterRegistry.timer("fraud.evaluation.duration"));
        }
    }
}
