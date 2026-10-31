package com.arielsoto.spendtracker.security;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.arielsoto.spendtracker.loggin.SecurityMdcFilter;

@ExtendWith(MockitoExtension.class)
class LogoutAuditHandlerTest {

    @Mock
    private SecurityAuditLogger auditLogger;

    private LogoutAuditHandler handler;

    private MockHttpServletRequest request;

    private final OAuth2AuthenticationToken token = new OAuth2AuthenticationToken(
        new DefaultOAuth2User(List.of(), Map.of("id", 42), "id"),
        List.of(),
        "github"
    );

    @BeforeEach
    void setUp() {
        handler = new LogoutAuditHandler(auditLogger);
        request = new MockHttpServletRequest();
    }

    @Test
    void logsTheAppUserIdStoredInTheSession() {
        UUID appUserId = UUID.randomUUID();
        request.getSession().setAttribute(SecurityMdcFilter.APP_USER_ID, appUserId);

        handler.logout(request, new MockHttpServletResponse(), token);

        verify(auditLogger).logout(appUserId);
    }

    @Test
    void logsWithoutUserIdWhenThereIsNoSession() {
        handler.logout(request, new MockHttpServletResponse(), token);

        verify(auditLogger).logout(null);
    }

    @Test
    void doesNotLogWhenNobodyIsLoggedIn() {
        handler.logout(request, new MockHttpServletResponse(), null);

        verifyNoInteractions(auditLogger);
    }
}
