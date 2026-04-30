package com.watchmyai.quota;

import com.watchmyai.user.UserContextService;
import com.watchmyai.user.UserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPlanServiceTest {

    private static final String USER_ID = "test-user";

    private UserPlanRepository userPlanRepository;
    private UserPlanService userPlanService;

    @BeforeEach
    void setUp() {
        userPlanRepository = mock(UserPlanRepository.class);
        UserContextService userContextService = mock(UserContextService.class);
        when(userContextService.getCurrentUser())
                .thenReturn(new UserIdentity(USER_ID));

        userPlanService = new UserPlanService(userPlanRepository, userContextService);
    }

    @Test
    void getCurrentPlanDefaultsToFreeWhenNoUserPlanExists() {
        when(userPlanRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty());

        PlanType planType = userPlanService.getCurrentPlan();

        assertThat(planType).isEqualTo(PlanType.FREE);
    }

    @Test
    void getCurrentPlanReturnsStoredUserPlan() {
        when(userPlanRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(new UserPlanEntity(USER_ID, PlanType.PRO)));

        PlanType planType = userPlanService.getCurrentPlan();

        assertThat(planType).isEqualTo(PlanType.PRO);
    }

    @Test
    void setCurrentPlanCreatesUserPlanWhenMissing() {
        when(userPlanRepository.findByUserId(USER_ID))
                .thenReturn(Optional.empty());

        PlanType planType = userPlanService.setCurrentPlan(PlanType.PLUS);

        assertThat(planType).isEqualTo(PlanType.PLUS);
        verify(userPlanRepository).save(any(UserPlanEntity.class));
    }
}
