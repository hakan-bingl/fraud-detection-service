package io.softpay.fraud.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.softpay.fraud.config.FraudRulesProperties;
import io.softpay.fraud.domain.RuleEvaluationResult;
import io.softpay.fraud.domain.TransactionContext;
import io.softpay.fraud.dto.RejectionStatus;
import io.softpay.fraud.dto.TransactionEvaluationRequest;
import io.softpay.fraud.dto.TransactionEvaluationResponse;
import io.softpay.fraud.repository.TransactionStateRepository;
import io.softpay.fraud.rule.FraudRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
public class FraudDetectionService {

    private final List<FraudRule> rules;
    private final CurrencyConversionService currencyConversionService;
    private final TransactionStateRepository transactionStateRepository;
    private final FraudRulesProperties properties;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public TransactionEvaluationResponse evaluate(TransactionEvaluationRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);
        Instant now = clock.instant();

        try {
            BigDecimal amountEur = currencyConversionService.convertToEur(request.amount(), request.currency());
            String hashedPan = CardSecurityUtils.hashPan(request.cardNumber(), properties.salt());

            TransactionContext context = new TransactionContext(
                    request,
                    amountEur,
                    hashedPan,
                    now
            );

            log.info("Evaluating transaction: terminalId={}, threatScore={}, amountEur={}, maskedPan={}",
                    request.terminalId(), request.terminalThreatScore(), amountEur, CardSecurityUtils.maskPan(request.cardNumber()));

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
