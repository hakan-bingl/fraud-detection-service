package io.softpay.fraud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "fraud.rules")
public record FraudRulesProperties(
    @DefaultValue("70") int rejectionThreshold,
    @DefaultValue("softpay-secret-salt-v1") String salt,
    @DefaultValue AmountRuleProperties amountRule,
    @DefaultValue TerminalVelocityProperties terminalVelocityRule,
    @DefaultValue CardVelocityProperties cardVelocityRule
) {

    public record AmountRuleProperties(
        @DefaultValue("5000") BigDecimal baseMaxAmountEur,
        @DefaultValue("0.90") double threatDeductionFactor,
        @DefaultValue("0.70") double warningThresholdRatio
    ) {}

    public record TerminalVelocityProperties(
        @DefaultValue("60") int windowSeconds,
        @DefaultValue("10") int baseMaxTxPerWindow,
        @DefaultValue("0.80") double threatDeductionFactor,
        @DefaultValue("2") int minAllowedTx,
        @DefaultValue("85") int score
    ) {}

    public record CardVelocityProperties(
        @DefaultValue("60") int windowSeconds,
        @DefaultValue("3") int maxTxPerWindow,
        @DefaultValue("90") int score
    ) {}
}
