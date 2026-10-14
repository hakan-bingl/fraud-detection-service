package io.softpay.fraud.repository;

import java.time.Instant;

public interface TransactionStateRepository {
    void recordTransaction(String terminalId, String hashedCardNumber, Instant timestamp);
    int getTerminalTransactionCount(String terminalId, Instant from, Instant to);
    int getCardTransactionCount(String hashedCardNumber, Instant from, Instant to);
    void clear();
}
