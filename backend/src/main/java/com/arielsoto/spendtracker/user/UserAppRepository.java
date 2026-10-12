package com.arielsoto.spendtracker.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserAppRepository extends JpaRepository<UserApp, UUID>{
    
    Optional<UserApp> findByOauthProviderAndOauthId(
        OAuthProvider oauthProvider,
        String oauthId
    );

    Optional<UserApp> findByEmail(String email);

    boolean existsByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM UserApp u WHERE u.id = :id")
    Optional<UserApp> findByIdForUpdate(@Param("id") UUID id);
}
