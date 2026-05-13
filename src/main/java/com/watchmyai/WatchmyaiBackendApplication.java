package com.watchmyai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class WatchmyaiBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatchmyaiBackendApplication.class, args);
    }

}
