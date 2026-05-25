package com.watchmyai.subscription;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Consumer;

/**
 * Persists a single subscription row in its own transaction. Running in
 * {@link Propagation#REQUIRES_NEW} ensures that a unique-constraint conflict — caused by a
 * concurrent insert of the same {@code originalTransactionId} — rolls back only this inner
 * transaction. The caller can then retry, and the retry finds the now-existing row and performs
 * a plain update instead of an insert.
 */
@Service
public class SubscriptionUpsertService {

    private final AppStoreSubscriptionRepository subscriptionRepository;

    public SubscriptionUpsertService(AppStoreSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void upsert(
            String userId,
            String originalTransactionId,
            Consumer<AppStoreSubscriptionEntity> applyUpdate
    ) {
        AppStoreSubscriptionEntity subscription = subscriptionRepository
                .findByOriginalTransactionId(originalTransactionId)
                .orElseGet(() -> new AppStoreSubscriptionEntity(userId, originalTransactionId));

        applyUpdate.accept(subscription);
        subscriptionRepository.saveAndFlush(subscription);
    }
}
