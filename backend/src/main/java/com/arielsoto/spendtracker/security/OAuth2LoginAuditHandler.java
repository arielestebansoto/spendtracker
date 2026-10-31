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
import org.springframework.web.util.UriComponentsBuilder;

import com.arielsoto.spendtracker.loggin.ClientIpResolver;
import com.arielsoto.spendtracker.loggin.SecurityMdcFilter;
import com.arielsoto.spendtracker.user.UserApp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class OAuth2LoginAuditHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    // The OAuth2 provider redirects back to /login/oauth2/code/{registrationId}
    private static final String CALLBACK_PATH_PREFIX = "/login/oauth2/code/";

    private static final String GENERIC_ERROR_CODE = "login_failed";

    private final AuthenticatedUserService authenticatedUserService;

    private final SecurityAuditLogger auditLogger;

    private final String defaultSuccessUrl;

    private final String failureUrl;

    public OAuth2LoginAuditHandler(
        AuthenticatedUserService authenticatedUserService,
        SecurityAuditLogger auditLogger,
        @Value("${app.oauth2.default-success-url}") String defaultSuccessUrl,
        @Value("${app.oauth2.failure-url}") String failureUrl
    ) {
        this.authenticatedUserService = authenticatedUserService;
        this.auditLogger = auditLogger;
        this.defaultSuccessUrl = defaultSuccessUrl;
        this.failureUrl = failureUrl;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException {

        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;

        UserApp user = authenticatedUserService.getCurrentUser(token);

        // The session was already migrated on login, so the attribute lands on the one the browser keeps
        request.getSession().setAttribute(SecurityMdcFilter.APP_USER_ID, user.getId());

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
    ) throws IOException {

        String reason = resolveReason(exception);

        auditLogger.loginFailed(
            resolveProvider(request),
            reason,
            ClientIpResolver.resolve(request)
        );

        // The browser lands here after the provider's redirect, so it is sent back to the frontend
        // to read why the login failed, instead of a blank 401 page.
        response.sendRedirect(
            UriComponentsBuilder.fromUriString(failureUrl)
                .queryParam("error", resolveErrorCode(exception))
                .toUriString()
        );
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

    // Only the OAuth2 error code goes in the URL; any other failure gets a generic code,
    // so Java class names stay in the log.
    private String resolveErrorCode(AuthenticationException exception) {

        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            return oauth2Exception.getError().getErrorCode();
        }

        return GENERIC_ERROR_CODE;
    }
}
