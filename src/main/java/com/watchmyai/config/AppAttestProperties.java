package com.watchmyai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Apple App Attest configuration. Bound from {@code watchmyai.app-attest.*}.
 *
 * <ul>
 *   <li>{@code teamId} / {@code bundleId} — must exactly match the signing identity of the
 *       iOS/watchOS app; the attestation certificate is validated against them.</li>
 *   <li>{@code production} — {@code true} once the app ships through the App Store /
 *       TestFlight (App Attest "production" environment); {@code false} for development builds
 *       run from Xcode (the attestation AAGUID differs between the two).</li>
 *   <li>{@code challengeTtlSeconds} — how long an issued attestation challenge stays valid.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "watchmyai.app-attest")
@SuppressWarnings("unused")
public class AppAttestProperties {

    private final String teamId;
    private final String bundleId;
    private final boolean production;
    private final int challengeTtlSeconds;

    public AppAttestProperties(String teamId, String bundleId, boolean production, int challengeTtlSeconds) {
        this.teamId = teamId == null ? "" : teamId.trim();
        this.bundleId = bundleId == null ? "" : bundleId.trim();
        this.production = production;
        this.challengeTtlSeconds = challengeTtlSeconds <= 0 ? 300 : challengeTtlSeconds;
    }

    public String teamId() {
        return teamId;
    }

    public String bundleId() {
        return bundleId;
    }

    public boolean production() {
        return production;
    }

    public Duration challengeTtl() {
        return Duration.ofSeconds(challengeTtlSeconds);
    }
}
