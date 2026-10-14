package io.softpay.fraud.repository;

public record Terminal(
        String terminalId,
        boolean active,
        String description
) {}
