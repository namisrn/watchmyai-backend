package com.watchmyai.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;

    public AppUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Transactional
    public AppUserEntity findOrCreateAppleUser(AppleUserIdentity appleUser, String appleUserId) {
        AppUserEntity user = appUserRepository
                .findByAppleSubject(appleUser.subject())
                .orElseGet(() -> new AppUserEntity(appleUser.subject(), appleUserId, appleUser.email()));

        user.updateAppleProfile(appleUserId, appleUser.email());
        return appUserRepository.save(user);
    }

    @Transactional(readOnly = true)
    public Optional<AppUserEntity> findByUserId(String userId) {
        return appUserRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Optional<AppUserEntity> findByAppAccountToken(UUID appAccountToken) {
        return appUserRepository.findByAppAccountToken(appAccountToken);
    }
}
