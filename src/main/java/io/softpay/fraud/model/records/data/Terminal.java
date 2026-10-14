package io.softpay.fraud.model.records.data;

public record Terminal(
        String terminalId,
        boolean active,
        String description
) {}
