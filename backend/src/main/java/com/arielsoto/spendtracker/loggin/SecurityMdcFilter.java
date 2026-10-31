package com.arielsoto.spendtracker.loggin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// Runs right after Spring Security's filter chain, so the session is authenticated, and
// before RequestLoggingFilter, so the http_request log also carries the userId.
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)
public class SecurityMdcFilter extends OncePerRequestFilter {

    public static final String USER_ID = "userId";

    // Session attribute holding the app's UserApp id, set on login by OAuth2LoginAuditHandler,
    // so it is known on every request without a DB lookup.
    public static final String APP_USER_ID = "appUserId";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        UUID appUserId = resolveAppUserId(request);

        if (appUserId != null) {
            MDC.put(USER_ID, appUserId.toString());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(USER_ID);
        }
    }

    private UUID resolveAppUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return null;
        }

        return (UUID) session.getAttribute(APP_USER_ID);
    }
}
