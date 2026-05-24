package com.watchmyai.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppStoreSubscriptionRepository extends JpaRepository<AppStoreSubscriptionEntity, Long> {

    Optional<AppStoreSubscriptionEntity> findByOriginalTransactionId(String originalTransactionId);

    @SuppressWarnings("unused")
    Optional<AppStoreSubscriptionEntity> findFirstByAppAccountToken(UUID appAccountToken);

    List<AppStoreSubscriptionEntity> findByUserIdAndActiveTrue(String userId);

    void deleteByUserId(String userId);
}
