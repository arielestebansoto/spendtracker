package com.arielsoto.spendtracker.security;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import com.arielsoto.spendtracker.user.OAuthProvider;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.user.UserAppRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuthenticatedUserService {

    private final UserAppRepository userAppRepository;

    public UserApp getCurrentUser(
        OAuth2AuthenticationToken authentication
    ) {

        String registrationId = authentication
            .getAuthorizedClientRegistrationId();

        OAuthProvider provider = resolveProvider(registrationId);

        OAuth2User principal = authentication.getPrincipal();

        String oauthId = resolveOauthId(provider, principal);

        return userAppRepository
            .findByOauthProviderAndOauthId(provider, oauthId)
            .orElseThrow();
    }

    private OAuthProvider resolveProvider(String registrationId) {

        return switch (registrationId) {
            case "github" -> OAuthProvider.GITHUB;
            case "google" -> OAuthProvider.GOOGLE;
            default -> throw new IllegalArgumentException(
                "Unsupported provider: " + registrationId
            );
        };
    }

    private String resolveOauthId(
        OAuthProvider provider,
        OAuth2User principal
    ) {

        return switch (provider) {
            case GITHUB -> principal.getAttribute("id").toString();
            case GOOGLE -> principal.getAttribute("sub").toString();
        };
    }
}