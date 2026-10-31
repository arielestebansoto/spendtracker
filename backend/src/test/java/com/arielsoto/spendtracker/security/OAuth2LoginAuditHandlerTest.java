package com.arielsoto.spendtracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.arielsoto.spendtracker.loggin.SecurityMdcFilter;
import com.arielsoto.spendtracker.user.UserApp;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginAuditHandlerTest {

    private static final String SUCCESS_URL = "http://localhost:3000/dashboard";

    private static final String FAILURE_URL = "http://localhost:3000";

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    @Mock
    private SecurityAuditLogger auditLogger;

    private OAuth2LoginAuditHandler handler;

    private MockHttpServletRequest request;

    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginAuditHandler(
            authenticatedUserService,
            auditLogger,
            SUCCESS_URL,
            FAILURE_URL
        );
        request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        response = new MockHttpServletResponse();
    }

    @Test
    void successLogsProviderAndAppUserIdThenRedirects() throws Exception {
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(
            new DefaultOAuth2User(List.of(), Map.of("id", 42), "id"),
            List.of(),
            "github"
        );
        UserApp user = UserApp.builder()
            .id(UUID.randomUUID())
            .oauthId("42")
            .build();
        when(authenticatedUserService.getCurrentUser(token)).thenReturn(user);

        handler.onAuthenticationSuccess(request, response, token);

        verify(auditLogger).loginSuccess("github", user.getId(), "203.0.113.7");
        assertThat(response.getRedirectedUrl()).isEqualTo(SUCCESS_URL);
    }

    @Test
    void successStoresTheAppUserIdInTheSessionForTheLogs() throws Exception {
        OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(
            new DefaultOAuth2User(List.of(), Map.of("id", 42), "id"),
            List.of(),
            "github"
        );
        UserApp user = UserApp.builder()
            .id(UUID.randomUUID())
            .oauthId("42")
            .build();
        when(authenticatedUserService.getCurrentUser(token)).thenReturn(user);

        handler.onAuthenticationSuccess(request, response, token);

        assertThat(request.getSession().getAttribute(SecurityMdcFilter.APP_USER_ID)).isEqualTo(user.getId());
    }

    @Test
    void oauth2FailureLogsProviderAndErrorCodeThenRedirectsToTheFrontendWithTheCode() throws Exception {
        request.setRequestURI("/login/oauth2/code/google");
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
            new OAuth2Error("access_denied", "The user denied access", null)
        );

        handler.onAuthenticationFailure(request, response, exception);

        verify(auditLogger).loginFailed("google", "access_denied", "203.0.113.7");
        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE_URL + "?error=access_denied");
    }

    @Test
    void otherFailureLogsExceptionTypeThenRedirectsWithAGenericCode() throws Exception {
        request.setRequestURI("/somewhere-else");

        handler.onAuthenticationFailure(
            request,
            response,
            new BadCredentialsException("bad")
        );

        verify(auditLogger).loginFailed("unknown", "BadCredentialsException", "203.0.113.7");
        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE_URL + "?error=login_failed");
    }

    @Test
    void notInvitedFailureIsLoggedThenRedirectsToTheFrontendWithTheReason() throws Exception {
        request.setRequestURI("/login/oauth2/code/github");
        OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
            new OAuth2Error(EmailAllowlist.NOT_INVITED)
        );

        handler.onAuthenticationFailure(request, response, exception);

        verify(auditLogger).loginFailed("github", "not_invited", "203.0.113.7");
        assertThat(response.getRedirectedUrl()).isEqualTo(FAILURE_URL + "?error=not_invited");
    }
}
