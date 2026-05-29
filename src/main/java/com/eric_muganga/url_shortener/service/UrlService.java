package com.eric_muganga.url_shortener.service;

import com.eric_muganga.url_shortener.dto.response.StatsResponse;
import com.eric_muganga.url_shortener.entity.Url;
import com.eric_muganga.url_shortener.exception.InvalidUrlException;
import com.eric_muganga.url_shortener.exception.UrlNotFoundException;
import com.eric_muganga.url_shortener.metrics.CacheMetrics;
import com.eric_muganga.url_shortener.repository.UrlRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.retry.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

@Service
@Slf4j
public class UrlService {

    private final UrlRepository urlRepository;
    private final RedisTemplate<String, Url> redisTemplate;
    private final Base62Encoder encoder;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final CacheMetrics metrics;
    private final CaffeineCacheManager caffeineCache;

    // Constants
    private static final String REDIS_KEY_PREFIX = "url:";
    private static final long REDIS_TTL_SECONDS = 3600; // 1 hour
    private static final String CACHE_NAME = "urls";

    @Autowired
    public UrlService(UrlRepository urlRepository,
                      RedisTemplate<String, Url> redisTemplate,
                      Base62Encoder encoder,
                      CircuitBreaker redisCacheCircuitBreaker,
                      Retry redisRetry,
                      CacheMetrics metrics,
                      CacheManager cacheManager) {
        this.urlRepository = urlRepository;
        this.redisTemplate = redisTemplate;
        this.encoder = encoder;
        this.circuitBreaker = redisCacheCircuitBreaker;
        this.retry = redisRetry;
        this.metrics = metrics;
        this.caffeineCache = (CaffeineCacheManager) cacheManager;
    }

    /**
     * CREATE: Shorten a URL
     *
     * Process:
     * 1. Validate the URL format
     * 2. Generate next ID from PostgreSQL sequence
     * 3. Encode ID to Base62 short code
     * 4. Insert into database (unique constraint prevents collisions)
     * 5. Cache in Redis (best-effort, non-blocking)
     *
     * @param originalUrl The long URL to shorten
     * @return The short code (e.g., "aB3x")
     * @throws InvalidUrlException if URL is malformed
     * @throws RuntimeException if collision occurs (extremely rare)
     */
    public String shortenUrl(String originalUrl) {
        if (!isValidUrl(originalUrl)) {
            throw new InvalidUrlException(
                    originalUrl,
                    "URL is malformed or uses an unsupported protocol"
            );
        }

        // Step 1: Get next ID from DB sequence (atomic, distributed-safe)
        Long nextId = getNextSequenceId();
        String shortCode = encoder.encode(nextId);

        // Step 2: Attempt to insert into DB
        Url url = new Url(shortCode, originalUrl);
        try {
            Url saved = urlRepository.save(url);
            log.info("Created short URL: {} -> {}", shortCode, originalUrl);

            // Step 3: Cache in Redis (async, non-blocking, failure-tolerant)
            cacheInRedis(saved);

            metrics.recordUrlCreated(shortCode);
            return shortCode;

        } catch (DataIntegrityViolationException e) {
            // This should be extremely rare if sequence is working correctly
            log.error("Collision detected for short code: {}. This indicates a serious bug.", shortCode, e);
            metrics.recordCollision(shortCode);
            throw new RuntimeException("Collision detected. This is extremely rare. Retry the request.", e);
        }
    }

    /**
     * READ: Retrieve original URL by short code
     *
     * Cache-Aside Pattern (Lazy Loading):
     * 1. Check L1 cache (Caffeine in-memory)
     * 2. Check L2 cache (Redis)
     * 3. Check L3 (PostgreSQL database)
     * 4. Populate caches on miss
     *
     * @param shortCode The short code (e.g., "aB3x")
     * @return The original long URL
     * @throws UrlNotFoundException if short code doesn't exist
     */
    public String getOriginalUrl(String shortCode) {
        metrics.recordLookupAttempt(shortCode);

        // L1: Caffeine in-memory cache (fastest, no network latency)
        Cache caffeineCache = this.caffeineCache.getCache(CACHE_NAME);
        if (caffeineCache != null) {
            Url cached = caffeineCache.get(shortCode, Url.class);
            if (cached != null && !cached.getIsDeleted()) {
                metrics.recordCacheHit("caffeine");
                log.debug("Cache hit (L1 Caffeine): {}", shortCode);
                return cached.getOriginalUrl();
            }
        }

        // L2: Redis (network latency ~2ms, but larger capacity)
        // Only attempt if circuit breaker is CLOSED (healthy)
        if (circuitBreaker.getState() == CircuitBreaker.State.CLOSED) {
            Url redisCached = getFromRedis(shortCode);
            if (redisCached != null && !redisCached.getIsDeleted()) {
                metrics.recordCacheHit("redis");
                log.debug("Cache hit (L2 Redis): {}", shortCode);

                // Populate L1 for next request
                if (caffeineCache != null) {
                    caffeineCache.put(shortCode, redisCached);
                }
                return redisCached.getOriginalUrl();
            }
        } else {
            log.warn("Redis circuit breaker is OPEN. Bypassing L2 cache.");
            metrics.recordCircuitBreakerOpen();
        }

        // L3: PostgreSQL database (slowest, ~50ms, but reliable)
        Url url = urlRepository.findActiveByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(
                        "Short code not found: " + shortCode,
                        shortCode
                ));

        metrics.recordCacheHit("database");
        log.debug("Cache miss, fetched from database: {}", shortCode);

        // Repopulate L1 and L2 for next request
        if (caffeineCache != null) {
            caffeineCache.put(shortCode, url);
        }
        cacheInRedis(url);

        return url.getOriginalUrl();
    }

    /**
     * UPDATE: Update the original URL mapped to a short code
     *
     * Invalidates all cache layers to prevent stale reads.
     *
     * @param shortCode The short code to update
     * @param newOriginalUrl The new URL to map to
     * @throws UrlNotFoundException if short code doesn't exist
     */
    public void updateUrl(String shortCode, String newOriginalUrl) {
        if (!isValidUrl(newOriginalUrl)) {
            throw new InvalidUrlException(
                    newOriginalUrl,
                    "New URL is malformed or uses an unsupported protocol"
            );
        }

        Url url = urlRepository.findActiveByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(
                        "Short code not found: " + shortCode,
                        shortCode
                ));

        url.setOriginalUrl(newOriginalUrl);
        urlRepository.save(url);

        // Invalidate all cache layers
        invalidateCache(shortCode);
        metrics.recordUrlUpdated(shortCode);
        log.info("URL updated: {} -> {}", shortCode, newOriginalUrl);
    }

    /**
     * DELETE: Soft delete a URL
     *
     * Marks the URL as deleted without removing database record (maintains audit trail).
     * Invalidates all cache layers.
     *
     * @param shortCode The short code to delete
     * @throws UrlNotFoundException if short code doesn't exist
     */
    public void deleteUrl(String shortCode) {
        Url url = urlRepository.findActiveByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(
                        "Short code not found: " + shortCode,
                        shortCode
                ));

        url.setIsDeleted(true);
        urlRepository.save(url);

        // Invalidate all cache layers
        invalidateCache(shortCode);
        metrics.recordUrlDeleted(shortCode);
        log.info("URL deleted (soft): {}", shortCode);
    }

    /**
     * STATS: Get access statistics for a short URL
     *
     * TODO: Implement access_logs table tracking
     * - Create access_logs table to track each redirect
     * - Log timestamp, user agent, IP address, referrer on each access
     * - Query access_logs to populate totalAccesses and lastAccessedAt
     *
     * @param shortCode The short code
     * @return StatsResponse with URL metadata and access statistics
     * @throws UrlNotFoundException if short code doesn't exist
     */
    public StatsResponse getStats(String shortCode) {
        Url url = urlRepository.findActiveByShortCode(shortCode)
                .orElseThrow(() -> new UrlNotFoundException(
                        "Short code not found: " + shortCode,
                        shortCode
                ));

        // TODO: Query access_logs table for stats
        return StatsResponse.builder()
                .shortCode(shortCode)
                .originalUrl(url.getOriginalUrl())
                .createdAt(url.getCreatedAt())
                .lastAccessedAt(null) // Placeholder
                .totalAccesses(0L)     // Placeholder
                .build();
    }

    // ============ HELPER METHODS ============

    /**
     * Get the next ID from the PostgreSQL sequence.
     *
     * Uses a native query to call NEXTVAL('url_id_sequence') directly,
     * avoiding the creation of dummy records.
     *
     * This is distributed-safe because PostgreSQL sequences are atomic
     * and guarantee no two calls will return the same value.
     *
     * @return The next unique ID
     */
    private Long getNextSequenceId() {
        Long id = urlRepository.getNextSequenceId();
        log.debug("Generated sequence ID: {}", id);
        return id;
    }

    /**
     * Cache a URL in Redis with circuit breaker and retry protection.
     *
     * Uses Resilience4j decorators to:
     * 1. Automatically retry on failure (up to 2 times)
     * 2. Open circuit breaker if Redis fails consistently
     * 3. Never block the main request (failures are logged, not thrown)
     *
     * @param url The URL entity to cache
     */
    private void cacheInRedis(Url url) {
        try {
            // Supplier: what to execute
            Supplier<Void> cacheOperation = () -> {
                String key = REDIS_KEY_PREFIX + url.getShortCode();
                redisTemplate.opsForValue().set(
                        key,
                        url,
                        Duration.ofSeconds(REDIS_TTL_SECONDS)
                );
                return null;
            };

            // Decorate with circuit breaker and retry
            Supplier<Void> decorated = Decorators.ofSupplier(cacheOperation)
                    .withCircuitBreaker(circuitBreaker)
                    .withRetry(retry)
                    .decorate();

            // Execute
            decorated.get();
            metrics.recordCacheWrite("redis");
            log.debug("Cached in Redis: {}", url.getShortCode());

        } catch (Exception e) {
            // Never throw: Redis failures should not block the response
            log.warn("Failed to cache in Redis: {}. Circuit breaker state: {}",
                    e.getMessage(),
                    circuitBreaker.getState());
            metrics.recordCacheWriteFailure("redis");
        }
    }

    /**
     * Retrieve a URL from Redis.
     *
     * Gracefully handles connection failures by returning null
     * (the request will fall through to the database).
     *
     * @param shortCode The short code
     * @return The cached URL entity, or null if not found/error
     */
    private Url getFromRedis(String shortCode) {
        try {
            String key = REDIS_KEY_PREFIX + shortCode;
            Url cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                log.debug("Retrieved from Redis: {}", shortCode);
            }
            return cached;
        } catch (Exception e) {
            log.warn("Redis read failed for {}: {}", shortCode, e.getMessage());
            return null;
        }
    }

    /**
     * Invalidate all cache layers for a short code.
     *
     * Called when a URL is updated or deleted to prevent stale reads.
     *
     * @param shortCode The short code to invalidate
     */
    private void invalidateCache(String shortCode) {
        // Invalidate L1: Caffeine
        Cache caffeineCache = this.caffeineCache.getCache(CACHE_NAME);
        if (caffeineCache != null) {
            caffeineCache.evict(shortCode);
            log.debug("Invalidated Caffeine cache: {}", shortCode);
        }

        // Invalidate L2: Redis
        try {
            String key = REDIS_KEY_PREFIX + shortCode;
            Boolean deleted = redisTemplate.delete(key);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Invalidated Redis cache: {}", shortCode);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate Redis cache for {}: {}", shortCode, e.getMessage());
        }

        metrics.recordCacheInvalidation(shortCode);
    }

    /**
     * Validate that a URL is well-formed and uses a supported protocol.
     *
     * Currently accepts: http, https
     *
     * @param urlString The URL to validate
     * @return true if valid, false otherwise
     */
    private boolean isValidUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }

        try {
            URL url = new URL(urlString);
            String protocol = url.getProtocol();
            // Only allow HTTP and HTTPS
            return "http".equals(protocol) || "https".equals(protocol);
        } catch (MalformedURLException e) {
            log.debug("Invalid URL format: {}", urlString);
            return false;
        }
    }
}
