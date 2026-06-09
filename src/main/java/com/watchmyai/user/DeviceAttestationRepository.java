package com.watchmyai.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceAttestationRepository extends JpaRepository<DeviceAttestationEntity, Long> {

    Optional<DeviceAttestationEntity> findByKeyId(String keyId);
}
