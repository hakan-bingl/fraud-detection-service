package io.softpay.fraud.port.out;

import io.softpay.fraud.model.records.data.Terminal;

import java.util.Optional;

public interface TerminalDataOut {
    Optional<Terminal> findByTerminalId(String terminalId);
    void save(Terminal terminal);
}
