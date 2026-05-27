package com.watchmyai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// Enables {@code @Scheduled} on Spring beans. Currently used by
// {@link com.watchmyai.ai.AiRequestLogRetentionJob} to purge personal data
// from {@code ai_request_log.answer} after the retention window defined in
// {@code legal/PRIVACY_POLICY.md} § 10. Adding new scheduled jobs requires no
// further configuration once this annotation is present.
@EnableScheduling
public class WatchmyaiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatchmyaiBackendApplication.class, args);
    }

}
