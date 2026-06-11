package com.watchmyai.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeviceAttestationRepository extends JpaRepository<DeviceAttestationEntity, Long> {

    Optional<DeviceAttestationEntity> findByKeyId(String keyId);

    /**
     * GDPR Art. 17 erasure: drop the attested-device record(s) tied to a user on account deletion.
     * After a guest→account migration {@link #reassignUser} has re-keyed the row to the account
     * userId, so deleting by the account userId also removes the migrated guest's attestation blob.
     */
    void deleteByUserId(String userId);

    /** Guest→account migration: keep the attested device linked to the now-signed-in account. */
    @Modifying
    @Query(value = "UPDATE device_attestation SET user_id = :accountUserId WHERE user_id = :guestUserId", nativeQuery = true)
    int reassignUser(@Param("guestUserId") String guestUserId, @Param("accountUserId") String accountUserId);
}
