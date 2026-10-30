package com.arielsoto.spendtracker.security;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.arielsoto.spendtracker.loggin.ClientIpResolver;
import com.arielsoto.spendtracker.user.UserApp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginAuditHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    // The OAuth2 provider redirects back to /login/oauth2/code/{registrationId}
    private static final String CALLBACK_PATH_PREFIX = "/login/oauth2/code/";

    private final AuthenticatedUserService authenticatedUserService;

    private final SecurityAuditLogger auditLogger;

    private final String defaultSuccessUrl;

    public OAuth2LoginAuditHandler(
        AuthenticatedUserService authenticatedUserService,
        SecurityAuditLogger auditLogger,
        @Value("${app.oauth2.default-success-url}") String defaultSuccessUrl
    ) {
        this.authenticatedUserService = authenticatedUserService;
        this.auditLogger = auditLogger;
        this.defaultSuccessUrl = defaultSuccessUrl;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;

        UserApp user = authenticatedUserService.getCurrentUser(token);

        auditLogger.loginSuccess(
            token.getAuthorizedClientRegistrationId(),
            user.getId(),
            ClientIpResolver.resolve(request)
        );

        response.sendRedirect(defaultSuccessUrl);
    }

    @Override
    public void onAuthenticationFailure(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) {

        auditLogger.loginFailed(
            resolveProvider(request),
            resolveReason(exception),
            ClientIpResolver.resolve(request)
        );

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }

    private String resolveProvider(HttpServletRequest request) {

        String path = request.getRequestURI();

        if (path == null || !path.startsWith(CALLBACK_PATH_PREFIX)) {
            return "unknown";
        }

        return path.substring(CALLBACK_PATH_PREFIX.length());
    }

    // The OAuth2 error code (e.g. access_denied) says why the login failed;
    // its description is left out because it can echo the provider's response.
    private String resolveReason(AuthenticationException exception) {

        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            return oauth2Exception.getError().getErrorCode();
        }

        return exception.getClass().getSimpleName();
    }
}
