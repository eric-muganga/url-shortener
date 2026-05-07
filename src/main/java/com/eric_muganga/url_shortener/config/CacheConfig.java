package com.eric_muganga.url_shortener.config;

import com.eric_muganga.url_shortener.entity.Url;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;

import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {
    /**
     * L1 Cache: In-memory (Caffeine)
     * Strategy: LRU, max 10,000 entries, 10-minute TTL
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("urls");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }

    /**
     * L2 Cache: Redis
     * Strategy: Manual management with circuit breaker fallback
     */
    @Bean
    public RedisTemplate<String, Url> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Url> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        JacksonJsonRedisSerializer<Url> serializer = new JacksonJsonRedisSerializer<>(Url.class);

        template.setDefaultSerializer(serializer);
        template.setValueSerializer(serializer);
        template.setKeySerializer(new StringRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
