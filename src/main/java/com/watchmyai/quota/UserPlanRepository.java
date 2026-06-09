package com.watchmyai.quota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserPlanRepository extends JpaRepository<UserPlanEntity, Long> {

    Optional<UserPlanEntity> findByUserId(String userId);

    boolean existsByUserId(String userId);

    void deleteByUserId(String userId);

    /** Guest→account migration: repoint a guest's plan row onto the account user id. */
    @Modifying
    @Query(value = "UPDATE user_plan SET user_id = :accountUserId WHERE user_id = :guestUserId", nativeQuery = true)
    int reassignUser(@Param("guestUserId") String guestUserId, @Param("accountUserId") String accountUserId);
}
