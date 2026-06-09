package com.watchmyai.config;

import com.watchmyai.common.api.ApiAuthenticationFilter;
import com.watchmyai.user.UserContextService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ApiSecurityConfig {

    @Bean
    ApiAuthenticationFilter apiAuthenticationFilter(ObjectProvider<UserContextService> userContextServiceProvider) {
        return new ApiAuthenticationFilter(userContextServiceProvider);
    }

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            ApiAuthenticationFilter apiAuthenticationFilter
    ) throws Exception {
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
                .addFilterBefore(apiAuthenticationFilter, AuthorizationFilter.class)
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/privacy", "/terms", "/impressum").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/privacy", "/terms", "/impressum").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/plans").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/device/attest/challenge").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/device/attest").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/apple").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/apple/notifications").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/app-store/notifications").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/telemetry/events").permitAll()
                        .requestMatchers("/api/v1/**").authenticated()
                        .anyRequest().denyAll()
                );

        return http.build();
    }
}
