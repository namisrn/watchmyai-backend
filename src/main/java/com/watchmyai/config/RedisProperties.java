package com.watchmyai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RedisProperties {

    private final String url;

    public RedisProperties(@Value("${REDIS_URL:}") String url) {
        this.url = url;
    }

    public boolean hasUrl() {
        return url != null && !url.isBlank();
    }
}
