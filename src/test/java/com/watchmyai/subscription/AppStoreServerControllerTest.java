package com.watchmyai.subscription;

import com.apple.itunes.storekit.model.Data;
import com.apple.itunes.storekit.model.JWSTransactionDecodedPayload;
import com.apple.itunes.storekit.model.ResponseBodyV2DecodedPayload;
import com.watchmyai.user.UserContextService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppStoreServerControllerTest {

    @Test
    void processingFailureReleasesNotificationReservationForAppleRetry() {
        AppStoreServerService appStoreServerService = mock(AppStoreServerService.class);
        SubscriptionEntitlementService entitlementService = mock(SubscriptionEntitlementService.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        AppStoreServerController controller = new AppStoreServerController(
                appStoreServerService,
                entitlementService,
                mock(UserContextService.class),
                redisTemplate,
                new SimpleMeterRegistry()
        );
        String payloadJws = "notification.header.signature";
        String transactionJws = "transaction.header.signature";
        String notificationId = "notification-1";
        String reservationKey = "watchmyai:appstore-notification:" + notificationId;
        ResponseBodyV2DecodedPayload payload = mock(ResponseBodyV2DecodedPayload.class);
        Data data = mock(Data.class);
        JWSTransactionDecodedPayload transaction = mock(JWSTransactionDecodedPayload.class);

        when(appStoreServerService.verifyNotificationPayload(payloadJws)).thenReturn(payload);
        when(payload.getData()).thenReturn(data);
        when(payload.getNotificationUUID()).thenReturn(notificationId);
        when(data.getSignedTransactionInfo()).thenReturn(transactionJws);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(reservationKey, "1", Duration.ofDays(7))).thenReturn(true);
        when(appStoreServerService.verifyClientTransactionPayload(transactionJws))
                .thenReturn(AppStoreServerService.VerificationResult.verified(transaction));
        when(entitlementService.applyNotification(payload, transaction))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> controller.notifications(new AppStoreNotificationRequest(payloadJws)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(redisTemplate).delete(reservationKey);
    }
}
