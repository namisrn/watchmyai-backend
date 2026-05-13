package com.watchmyai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "watchmyai.debug")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class DebugProperties {

    private final boolean endpointsEnabled;

    public DebugProperties(boolean endpointsEnabled) {
        this.endpointsEnabled = endpointsEnabled;
    }

}
