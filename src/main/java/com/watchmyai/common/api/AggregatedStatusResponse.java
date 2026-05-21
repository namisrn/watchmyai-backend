package com.watchmyai.common.api;

import com.watchmyai.quota.QuotaStatusResponse;
import com.watchmyai.subscription.AppStoreServerStatusResponse;
import com.watchmyai.subscription.SubscriptionStatusResponse;
import com.watchmyai.user.AuthStatusResponse;

/**
 * Combined status payload returned by {@code GET /api/v1/status}. Aggregates the four
 * individual status endpoints so the iOS and watchOS clients need a single round trip.
 */
public record AggregatedStatusResponse(
        AuthStatusResponse auth,
        SubscriptionStatusResponse subscription,
        QuotaStatusResponse quota,
        AppStoreServerStatusResponse appStore
) {
}
