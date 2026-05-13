package com.watchmyai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "watchmyai.session")
@SuppressWarnings("unused")
public class SessionProperties {

    private final int ttlDays;

    public SessionProperties(int ttlDays) {
        this.ttlDays = Math.max(ttlDays, 1);
    }

    public int ttlDays() {
        return ttlDays;
    }

    public Duration ttl() {
        return Duration.ofDays(ttlDays);
    }
}
