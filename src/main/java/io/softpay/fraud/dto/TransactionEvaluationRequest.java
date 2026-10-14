package io.softpay.fraud.dto;

import io.softpay.fraud.service.CardSecurityUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

@Schema(description = "Inflight transaction evaluation request payload")
public record TransactionEvaluationRequest(

    @Schema(description = "Transaction amount (strictly positive)", example = "250.00")
    @NotNull(message = "amount is required")
    @Positive(message = "amount must be strictly positive")
    BigDecimal amount,

    @Schema(description = "3-letter ISO-4217 currency code", example = "DKK")
    @NotBlank(message = "currency is required")
    @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a valid 3-letter ISO-4217 code")
    String currency,

    @Schema(description = "Unique identifier of the payment terminal", example = "TERM-001")
    @NotBlank(message = "terminalId is required")
    String terminalId,

    @Schema(description = "Current threat/risk score of the terminal (0 to 100)", example = "15")
    @NotNull(message = "terminalThreatScore is required")
    @Min(value = 0, message = "terminalThreatScore must be between 0 and 100")
    @Max(value = 100, message = "terminalThreatScore must be between 0 and 100")
    Integer terminalThreatScore,

    @Schema(description = "Card PAN (13 to 19 digits)", example = "4532015112831111")
    @NotBlank(message = "cardNumber is required")
    @Pattern(regexp = "^[0-9]{13,19}$", message = "cardNumber must be between 13 and 19 digits")
    String cardNumber
) {

    @Override
    public String toString() {
        return "TransactionEvaluationRequest[" +
                "amount=" + amount +
                ", currency='" + currency + '\'' +
                ", terminalId='" + terminalId + '\'' +
                ", terminalThreatScore=" + terminalThreatScore +
                ", cardNumber=" + CardSecurityUtils.maskPan(cardNumber) +
                ']';
    }
}
