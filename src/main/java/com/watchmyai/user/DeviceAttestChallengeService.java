package com.watchmyai.user;

import com.watchmyai.config.AppAttestProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Issues and one-time-consumes App Attest challenges. Backed by Redis so a challenge issued by
 * one instance can be consumed by another, and so challenges auto-expire (replay protection).
 */
@Service
public class DeviceAttestChallengeService {

    private static final String KEY_PREFIX = "appattest:challenge:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final AppAttestProperties properties;

    public DeviceAttestChallengeService(StringRedisTemplate redisTemplate, AppAttestProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public String issueChallenge() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(KEY_PREFIX + challenge, "1", properties.challengeTtl());
        return challenge;
    }

    /**
     * Atomically removes the challenge. Returns {@code true} only if it existed (i.e. was issued
     * by us, not yet consumed, and not expired). A {@code false} means replay or forgery.
     */
    public boolean consumeChallenge(String challenge) {
        if (challenge == null || challenge.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redisTemplate.delete(KEY_PREFIX + challenge));
    }
}
