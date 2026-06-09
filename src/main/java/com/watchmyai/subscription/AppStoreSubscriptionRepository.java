package com.watchmyai.subscription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppStoreSubscriptionRepository extends JpaRepository<AppStoreSubscriptionEntity, Long> {

    Optional<AppStoreSubscriptionEntity> findByOriginalTransactionId(String originalTransactionId);

    @SuppressWarnings("unused")
    Optional<AppStoreSubscriptionEntity> findFirstByAppAccountToken(UUID appAccountToken);

    List<AppStoreSubscriptionEntity> findByUserIdAndActiveTrue(String userId);

    void deleteByUserId(String userId);

    /**
     * Guest→account migration: repoint a guest's App Store subscriptions onto the account user
     * id. {@code original_transaction_id} is globally unique, so this never collides.
     */
    @Modifying
    @Query(value = "UPDATE app_store_subscription SET user_id = :accountUserId WHERE user_id = :guestUserId", nativeQuery = true)
    int reassignUser(@Param("guestUserId") String guestUserId, @Param("accountUserId") String accountUserId);
}
