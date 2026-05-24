package com.watchmyai.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUserEntity, Long> {

    Optional<AppUserEntity> findByAppleSubject(String appleSubject);

    Optional<AppUserEntity> findByUserId(String userId);

    Optional<AppUserEntity> findByAppAccountToken(UUID appAccountToken);

    void deleteByUserId(String userId);
}
