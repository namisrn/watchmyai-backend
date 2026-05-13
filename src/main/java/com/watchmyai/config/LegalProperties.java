package com.watchmyai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "watchmyai.legal")
public class LegalProperties {

    private final String contactEmail;

    public LegalProperties(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String contactEmail() {
        return contactEmail;
    }
}
