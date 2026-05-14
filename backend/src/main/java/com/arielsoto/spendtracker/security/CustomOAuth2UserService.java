package com.arielsoto.spendtracker.security;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.arielsoto.spendtracker.user.OAuthProvider;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.user.UserAppRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserAppRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        System.out.println("CUSTOM_OAUTH2_USER_SERVICE_LOADINNG_USER");
        OAuth2User oauthUser = super.loadUser(userRequest);

        String registrationId = userRequest
            .getClientRegistration()
            .getRegistrationId();

        OAuthProvider provider = OAuthProvider.valueOf(registrationId.toUpperCase());

        String oauthId = extractOAuthId(provider, oauthUser);

        userRepository
            .findByOauthProviderAndOauthId(provider, oauthId)
            .orElseGet(() -> createUser(provider, oauthId, oauthUser));

        return oauthUser;
    }

    private String extractOAuthId(OAuthProvider provider, OAuth2User oauthUser) {
        return switch(provider) {
            case GOOGLE -> (String) oauthUser.getAttribute("sub");
            case GITHUB -> oauthUser.getAttribute("id").toString();
        };
    }

    private UserApp createUser(OAuthProvider provider, String oauthId, OAuth2User oauthUser) {
        String email = oauthUser.getAttribute("email");
        String name = oauthUser.getAttribute("name");

        UserApp user = UserApp.builder()
                .oauthProvider(provider)
                .oauthId(oauthId)
                .email(email)
                .name(name)
                .build();

        return userRepository.save(user);
    }
    
}
