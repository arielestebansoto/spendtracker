package com.arielsoto.spendtracker.security;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.arielsoto.spendtracker.user.OAuthProvider;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.user.UserAppRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {
    
    private final UserAppRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        
        OidcUser oidcUser = super.loadUser(userRequest);

        String oauthId = oidcUser.getSubject();

        userRepository
            .findByOauthProviderAndOauthId(OAuthProvider.GOOGLE, oauthId)
            .orElseGet(() -> createUser(oauthId, oidcUser));

        return oidcUser;
    }

    private UserApp createUser(String oauthId, OidcUser oidcUser) {

        UserApp user = UserApp.builder()
            .oauthProvider(OAuthProvider.GOOGLE)
            .oauthId(oauthId)
            .email(oidcUser.getEmail())
            .name(oidcUser.getFullName())
            .build();

        return userRepository.save(user);
    }
}
