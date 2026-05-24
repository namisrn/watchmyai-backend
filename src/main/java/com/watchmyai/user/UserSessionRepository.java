package com.watchmyai.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long> {

    Optional<UserSessionEntity> findByTokenHash(String tokenHash);

    void deleteByUserId(String userId);
}
