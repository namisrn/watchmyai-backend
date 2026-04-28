package com.watchmyai.quota;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserUsageRepository extends JpaRepository<UserUsageEntity, Long> {

    Optional<UserUsageEntity> findByUserIdAndPeriodYearMonth(
            String userId,
            String periodYearMonth
    );
}