package io.softpay.fraud.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Configuration for Valkey (Redis-compatible) high-speed in-memory store.
 * Valkey utilizes the standard RESP2/RESP3 wire protocol supported natively by Spring Data Redis.
 */
@Configuration
public class ValkeyConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
