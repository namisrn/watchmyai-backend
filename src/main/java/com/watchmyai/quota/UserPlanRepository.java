package com.watchmyai.quota;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPlanRepository extends JpaRepository<UserPlanEntity, Long> {

    Optional<UserPlanEntity> findByUserId(String userId);
}
