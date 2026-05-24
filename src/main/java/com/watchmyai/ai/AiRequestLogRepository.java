package com.watchmyai.ai;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiRequestLogRepository extends JpaRepository<AiRequestLogEntity, Long> {

    Optional<AiRequestLogEntity> findByUserIdAndClientRequestId(
            String userId,
            String clientRequestId
    );

    void deleteByUserId(String userId);
}
