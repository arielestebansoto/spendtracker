package com.arielsoto.spendtracker.security;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

import com.arielsoto.spendtracker.loggin.SecurityMdcFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

// A LogoutHandler, not the logout success handler, because it runs before the session is
// invalidated, while the app user id stored on login is still in it.
@Component
@RequiredArgsConstructor
public class LogoutAuditHandler implements LogoutHandler {

    private final SecurityAuditLogger auditLogger;

    @Override
    public void logout(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) {

        if (authentication == null) {
            return;
        }

        auditLogger.logout(resolveAppUserId(request));
    }

    private UUID resolveAppUserId(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session == null) {
            return null;
        }

        return (UUID) session.getAttribute(SecurityMdcFilter.APP_USER_ID);
    }
}
