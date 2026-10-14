package io.softpay.fraud;

import io.softpay.fraud.persistence.repository.ValkeySlidingWindowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValkeySlidingWindowRepositoryTest {

    private StringRedisTemplate redisTemplate;
    @SuppressWarnings("unchecked")
    private ZSetOperations<String, String> zSetOperations;
    private ValkeySlidingWindowRepository repository;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        zSetOperations = (ZSetOperations<String, String>) mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        repository = new ValkeySlidingWindowRepository(redisTemplate);
    }

    @Test
    @DisplayName("Should record terminal and card transactions into Valkey Sorted Sets with TTL")
    void testRecordTransaction() {
        Instant now = Instant.parse("2026-10-14T12:00:00Z");
        String terminalId = "TERM-001";
        String cardHash = "hash123";

        repository.recordTransaction(terminalId, cardHash, now);

        // Verify terminal ZSET add, expire, and prune
        verify(zSetOperations).add(eq("fraud:velocity:term:TERM-001"), anyString(), eq((double) now.toEpochMilli()));
        verify(redisTemplate).expire(eq("fraud:velocity:term:TERM-001"), eq(300L), eq(TimeUnit.SECONDS));
        verify(zSetOperations).removeRangeByScore(eq("fraud:velocity:term:TERM-001"), eq(0.0), anyDouble());

        // Verify card ZSET add, expire, and prune
        verify(zSetOperations).add(eq("fraud:velocity:card:hash123"), anyString(), eq((double) now.toEpochMilli()));
        verify(redisTemplate).expire(eq("fraud:velocity:card:hash123"), eq(300L), eq(TimeUnit.SECONDS));
        verify(zSetOperations).removeRangeByScore(eq("fraud:velocity:card:hash123"), eq(0.0), anyDouble());
    }

    @Test
    @DisplayName("Should query terminal transaction count using Valkey ZCOUNT")
    void testGetTerminalCount() {
        Instant from = Instant.parse("2026-10-14T11:59:00Z");
        Instant to = Instant.parse("2026-10-14T12:00:00Z");

        when(zSetOperations.count("fraud:velocity:term:TERM-001", from.toEpochMilli(), to.toEpochMilli()))
                .thenReturn(4L);

        int count = repository.getTerminalTransactionCount("TERM-001", from, to);
        assertThat(count).isEqualTo(4);
    }

    @Test
    @DisplayName("Should query card transaction count using Valkey ZCOUNT")
    void testGetCardCount() {
        Instant from = Instant.parse("2026-10-14T11:59:00Z");
        Instant to = Instant.parse("2026-10-14T12:00:00Z");

        when(zSetOperations.count("fraud:velocity:card:cardhash", from.toEpochMilli(), to.toEpochMilli()))
                .thenReturn(2L);

        int count = repository.getCardTransactionCount("cardhash", from, to);
        assertThat(count).isEqualTo(2);
    }
}
