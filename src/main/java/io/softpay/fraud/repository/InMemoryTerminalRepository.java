package io.softpay.fraud.repository;

import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryTerminalRepository implements TerminalRepository {

    private final Map<String, Terminal> store = new ConcurrentHashMap<>();

    public InMemoryTerminalRepository() {
        save(new Terminal("TERM-001", true, "Standard POS Terminal 1"));
        save(new Terminal("TERM-002", true, "Standard POS Terminal 2"));
        save(new Terminal("TERM-HIGH-RISK", true, "Flagged High Risk Location"));
        save(new Terminal("TERM-SUSPENDED", false, "Suspended Terminal"));
    }

    @Override
    public Optional<Terminal> findByTerminalId(String terminalId) {
        if (terminalId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(terminalId));
    }

    @Override
    public void save(Terminal terminal) {
        if (terminal != null && terminal.terminalId() != null) {
            store.put(terminal.terminalId(), terminal);
        }
    }
}
