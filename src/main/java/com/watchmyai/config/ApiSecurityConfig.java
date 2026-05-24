package com.watchmyai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ApiSecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF protection is intentionally disabled: this service is a stateless REST
                // API consumed only by native iOS/watchOS clients via opaque bearer tokens
                // (`Authorization: Bearer …`). There is no browser session cookie that an
                // attacker could ride, which is the only CSRF threat model. Reintroduce CSRF
                // tokens if we ever add a browser-rendered surface (web onboarding, OAuth
                // redirect handler, etc.). [S2-6 audit]
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .logout(logout -> logout.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/privacy", "/terms").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/plans").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/apple").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/app-store/notifications").permitAll()
                        // Bearer/session validation remains centralized in UserContextService so
                        // controllers keep their existing dev/test and opaque-session behavior.
                        .requestMatchers("/api/v1/**").permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }
}
