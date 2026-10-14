package io.softpay.fraud.repository;

import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Repository
public class InMemorySlidingWindowRepository implements TransactionStateRepository {

    private final Map<String, ConcurrentLinkedDeque<Instant>> terminalEvents = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<Instant>> cardEvents = new ConcurrentHashMap<>();

    @Override
    public void recordTransaction(String terminalId, String hashedCardNumber, Instant timestamp) {
        if (terminalId != null && timestamp != null) {
            terminalEvents.computeIfAbsent(terminalId, k -> new ConcurrentLinkedDeque<>()).addLast(timestamp);
            pruneOld(terminalEvents.get(terminalId), timestamp.minus(300, ChronoUnit.SECONDS));
        }
        if (hashedCardNumber != null && timestamp != null) {
            cardEvents.computeIfAbsent(hashedCardNumber, k -> new ConcurrentLinkedDeque<>()).addLast(timestamp);
            pruneOld(cardEvents.get(hashedCardNumber), timestamp.minus(300, ChronoUnit.SECONDS));
        }
    }

    @Override
    public int getTerminalTransactionCount(String terminalId, Instant from, Instant to) {
        if (terminalId == null) return 0;
        ConcurrentLinkedDeque<Instant> deque = terminalEvents.get(terminalId);
        if (deque == null || deque.isEmpty()) return 0;

        int count = 0;
        for (Instant t : deque) {
            if (!t.isBefore(from) && !t.isAfter(to)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public int getCardTransactionCount(String hashedCardNumber, Instant from, Instant to) {
        if (hashedCardNumber == null) return 0;
        ConcurrentLinkedDeque<Instant> deque = cardEvents.get(hashedCardNumber);
        if (deque == null || deque.isEmpty()) return 0;

        int count = 0;
        for (Instant t : deque) {
            if (!t.isBefore(from) && !t.isAfter(to)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void clear() {
        terminalEvents.clear();
        cardEvents.clear();
    }

    private void pruneOld(ConcurrentLinkedDeque<Instant> deque, Instant threshold) {
        if (deque == null) return;
        while (!deque.isEmpty()) {
            Instant oldest = deque.peekFirst();
            if (oldest != null && oldest.isBefore(threshold)) {
                deque.pollFirst();
            } else {
                break;
            }
        }
    }
}
