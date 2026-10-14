package io.softpay.fraud.persistence.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Valkey (RESP3-compatible) sliding window repository.
 * Utilizes Redis Sorted Sets (ZSET) for sub-millisecond distributed velocity tracking across Kubernetes pods.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ValkeySlidingWindowRepository implements TransactionStateRepository {

    private static final String TERMINAL_KEY_PREFIX = "fraud:velocity:term:";
    private static final String CARD_KEY_PREFIX = "fraud:velocity:card:";
    private static final long RETENTION_SECONDS = 300; // 5-minute key retention horizon

    private final StringRedisTemplate redisTemplate;

    @Override
    public void recordTransaction(String terminalId, String hashedCardNumber, Instant timestamp) {
        if (timestamp == null) {
            return;
        }

        long score = timestamp.toEpochMilli();
        long pruneHorizon = timestamp.minus(RETENTION_SECONDS, ChronoUnit.SECONDS).toEpochMilli();

        if (terminalId != null && !terminalId.isBlank()) {
            String key = TERMINAL_KEY_PREFIX + terminalId;
            String member = UUID.randomUUID() + ":" + score;
            try {
                redisTemplate.opsForZSet().add(key, member, score);
                redisTemplate.expire(key, RETENTION_SECONDS, TimeUnit.SECONDS);
                redisTemplate.opsForZSet().removeRangeByScore(key, 0, pruneHorizon);
            } catch (Exception e) {
                log.error("Failed to record terminal transaction in Valkey: key={}, error={}", key, e.getMessage());
            }
        }

        if (hashedCardNumber != null && !hashedCardNumber.isBlank()) {
            String key = CARD_KEY_PREFIX + hashedCardNumber;
            String member = UUID.randomUUID() + ":" + score;
            try {
                redisTemplate.opsForZSet().add(key, member, score);
                redisTemplate.expire(key, RETENTION_SECONDS, TimeUnit.SECONDS);
                redisTemplate.opsForZSet().removeRangeByScore(key, 0, pruneHorizon);
            } catch (Exception e) {
                log.error("Failed to record card transaction in Valkey: key={}, error={}", key, e.getMessage());
            }
        }
    }

    @Override
    public int getTerminalTransactionCount(String terminalId, Instant from, Instant to) {
        if (terminalId == null || terminalId.isBlank() || from == null || to == null) {
            return 0;
        }

        String key = TERMINAL_KEY_PREFIX + terminalId;
        try {
            Long count = redisTemplate.opsForZSet().count(key, from.toEpochMilli(), to.toEpochMilli());
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("Failed to count terminal transactions in Valkey: key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    @Override
    public int getCardTransactionCount(String hashedCardNumber, Instant from, Instant to) {
        if (hashedCardNumber == null || hashedCardNumber.isBlank() || from == null || to == null) {
            return 0;
        }

        String key = CARD_KEY_PREFIX + hashedCardNumber;
        try {
            Long count = redisTemplate.opsForZSet().count(key, from.toEpochMilli(), to.toEpochMilli());
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.error("Failed to count card transactions in Valkey: key={}, error={}", key, e.getMessage());
            return 0;
        }
    }

    @Override
    public void clear() {
        try {
            Set<String> terminalKeys = redisTemplate.keys(TERMINAL_KEY_PREFIX + "*");
            if (!terminalKeys.isEmpty()) {
                redisTemplate.delete(terminalKeys);
            }
            Set<String> cardKeys = redisTemplate.keys(CARD_KEY_PREFIX + "*");
            if (!cardKeys.isEmpty()) {
                redisTemplate.delete(cardKeys);
            }
        } catch (Exception e) {
            log.error("Failed to clear Valkey sliding window keys: error={}", e.getMessage());
        }
    }
}
