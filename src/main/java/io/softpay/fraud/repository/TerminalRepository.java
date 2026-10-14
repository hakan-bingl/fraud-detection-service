package io.softpay.fraud.repository;

import java.util.Optional;

public interface TerminalRepository {
    Optional<Terminal> findByTerminalId(String terminalId);
    void save(Terminal terminal);
}
