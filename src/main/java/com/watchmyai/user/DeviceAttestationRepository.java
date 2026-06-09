package com.watchmyai.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DeviceAttestationRepository extends JpaRepository<DeviceAttestationEntity, Long> {

    Optional<DeviceAttestationEntity> findByKeyId(String keyId);

    /** Guest→account migration: keep the attested device linked to the now-signed-in account. */
    @Modifying
    @Query(value = "UPDATE device_attestation SET user_id = :accountUserId WHERE user_id = :guestUserId", nativeQuery = true)
    int reassignUser(@Param("guestUserId") String guestUserId, @Param("accountUserId") String accountUserId);
}
