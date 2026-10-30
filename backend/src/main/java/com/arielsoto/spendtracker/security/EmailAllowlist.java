package com.arielsoto.spendtracker.security;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Component;

/**
 * Lets only invited people log in, so open sign-up cannot run up the AI bill.
 *
 * <p>Checked on every login, before the UserApp is loaded or created: removing an email revokes access from
 * the next login on, while sessions already open run until they expire.
 */
@Component
public class EmailAllowlist {

    public static final String NOT_INVITED = "not_invited";

    private final AllowedEmailRepository allowedEmailRepository;

    private final boolean enabled;

    public EmailAllowlist(
        AllowedEmailRepository allowedEmailRepository,
        @Value("${app.access.allowlist-enabled}") boolean enabled
    ) {
        this.allowedEmailRepository = allowedEmailRepository;
        this.enabled = enabled;
    }

    /**
     * @param email the provider's email; null when the user keeps it private (GitHub)
     * @param emailVerified whether the provider checked that the user owns the email
     * @throws OAuth2AuthenticationException with the {@value #NOT_INVITED} error code when not invited
     */
    public void requireInvited(String email, boolean emailVerified) {

        if (!enabled) {
            return;
        }

        if (email == null || !emailVerified || !isAllowed(email)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(NOT_INVITED));
        }
    }

    private boolean isAllowed(String email) {
        return allowedEmailRepository.existsById(email.toLowerCase(Locale.ROOT));
    }
}
