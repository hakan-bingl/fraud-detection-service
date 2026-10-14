package io.softpay.fraud.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;

/**
 * Service that normalizes transaction amounts into base currency (EUR)
 * for consistent rule threshold evaluation.
 */
@Service
public class CurrencyConversionService {

    private static final Map<String, BigDecimal> RATES_TO_EUR = Map.of(
            "EUR", BigDecimal.ONE,
            "DKK", new BigDecimal("0.1340"),
            "USD", new BigDecimal("0.9200"),
            "GBP", new BigDecimal("1.1700"),
            "SEK", new BigDecimal("0.0880"),
            "NOK", new BigDecimal("0.0860")
    );

    /**
     * Converts a given amount in specified currency to base currency (EUR).
     *
     * @param amount   amount in source currency
     * @param currency 3-letter ISO-4217 currency code
     * @return equivalent amount in EUR, rounded to 2 decimal places
     */
    public BigDecimal convertToEur(BigDecimal amount, String currency) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Currency cannot be blank");
        }

        String code = currency.trim().toUpperCase(Locale.ROOT);
        BigDecimal rate = RATES_TO_EUR.get(code);
        if (rate == null) {
            throw new IllegalArgumentException("Unsupported currency code: " + currency);
        }

        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}
