package com.arielsoto.spendtracker.security;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityAuditLogger {

    public void loginSuccess(String provider, UUID userId, String ip) {
        log.info(
            "security_event type=LOGIN_SUCCESS provider={} userId={} ip={}",
            provider,
            userId,
            ip
        );
    }

    public void loginFailed(String provider, String reason, String ip) {
        log.warn(
            "security_event type=LOGIN_FAILED provider={} reason={} ip={}",
            provider,
            reason,
            ip
        );
    }

    public void unauthorized(String path, String ip) {
        log.warn(
            "security_event type=UNAUTHORIZED path={} ip={}",
            path,
            ip
        );
    }


    public void accessDenied(String path, String ip) {
        log.warn(
            "security_event type=ACCESS_DENIED path={} ip={}",
            path,
            ip
        );
    }
    
    public void logout(String user) {
        log.info(
            "security_event type=LOGOUT user={}",
            user
        );
    }
}