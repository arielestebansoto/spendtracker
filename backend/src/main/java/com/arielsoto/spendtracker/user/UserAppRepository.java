package com.arielsoto.spendtracker.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAppRepository extends JpaRepository<UserApp, UUID>{
    
    Optional<UserApp> findByOauthProviderAndOauthId(
        String oauthProvider,
        String oauthId
    );

    Optional<UserApp> findByEmail(String email);

    boolean existsByEmail(String email);
}
