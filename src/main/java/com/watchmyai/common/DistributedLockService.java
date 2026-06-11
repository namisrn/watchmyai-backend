package com.watchmyai.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Best-effort distributed mutex backed by Redis ({@code SET key token NX EX ttl}). Makes the daily
 * retention purges safe under horizontal scaling: only one pod runs a given purge per window, the
 * others skip it. When no Redis is configured (dev/test, single instance) the task simply runs
 * directly. The purges are idempotent, so the lock only avoids duplicate bulk DML — it is never a
 * correctness gate, which is why every failure path falls back to running the task.
 */
@Component
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);
    private static final String KEY_PREFIX = "watchmyai:lock:";

    private final StringRedisTemplate redisTemplate;

    public DistributedLockService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /**
     * Runs {@code task} at most once across the cluster per {@code ttl} window for the given
     * {@code lockKey}. If the lock is held elsewhere the task is skipped; if Redis is absent or
     * erroring the task runs unguarded (safe because callers are idempotent).
     */
    public void runExclusively(String lockKey, Duration ttl, Runnable task) {
        if (redisTemplate == null) {
            task.run();
            return;
        }

        String redisKey = KEY_PREFIX + lockKey;
        String token = UUID.randomUUID().toString();
        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, token, ttl);
        } catch (DataAccessException exception) {
            log.warn("Distributed lock '{}' unavailable ({}); running task unguarded.", lockKey, exception.getMessage());
            task.run();
            return;
        }

        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("Distributed lock '{}' held by another instance; skipping task.", lockKey);
            return;
        }

        try {
            task.run();
        } finally {
            releaseIfOwner(redisKey, token);
        }
    }

    private void releaseIfOwner(String redisKey, String token) {
        try {
            if (token.equals(redisTemplate.opsForValue().get(redisKey))) {
                redisTemplate.delete(redisKey);
            }
        } catch (DataAccessException exception) {
            // The TTL expires the key anyway, so a failed release is harmless.
            log.debug("Could not release distributed lock '{}': {}", redisKey, exception.getMessage());
        }
    }
}
