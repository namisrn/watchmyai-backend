package com.watchmyai.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppStoreSubscriptionRepository extends JpaRepository<AppStoreSubscriptionEntity, Long> {

    Optional<AppStoreSubscriptionEntity> findByOriginalTransactionId(String originalTransactionId);

    List<AppStoreSubscriptionEntity> findByUserIdAndActiveTrue(String userId);
}
