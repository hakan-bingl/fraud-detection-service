package io.softpay.fraud.port.in;

import io.softpay.fraud.model.records.request.TransactionEvaluationRequest;
import io.softpay.fraud.model.records.response.TransactionEvaluationResponse;

public interface FraudDetectionIn {
    TransactionEvaluationResponse evaluate(TransactionEvaluationRequest request);
}
