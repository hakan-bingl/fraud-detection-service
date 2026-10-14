package io.softpay.fraud.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Fraud evaluation outcome")
public record TransactionEvaluationResponse(

    @Schema(description = "Outcome status (ALLOWED or REJECTED)", example = "ALLOWED")
    RejectionStatus rejectionStatus,

    @Schema(description = "Human-readable explanation or rejection reason", example = "Transaction approved")
    String rejectionMessage,

    @Schema(description = "Calculated composite fraud risk score (0 to 100)", example = "0")
    int fraudScore
) {

    @Schema(description = "Convenience boolean indicating if transaction was rejected", example = "false")
    @JsonProperty("rejected")
    public boolean isRejected() {
        return rejectionStatus == RejectionStatus.REJECTED;
    }
}
