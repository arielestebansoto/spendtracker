package com.arielsoto.spendtracker.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@ExtendWith(MockitoExtension.class)
class EmailAllowlistTest {

    @Mock
    private AllowedEmailRepository allowedEmailRepository;

    @Test
    void allowsAnInvitedEmailWhateverItsCase() {
        when(allowedEmailRepository.existsById("invited@example.com")).thenReturn(true);

        assertThatCode(() -> enabled().requireInvited("Invited@Example.com", true))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnEmailNotInTheAllowlist() {
        when(allowedEmailRepository.existsById("stranger@example.com")).thenReturn(false);

        assertNotInvited(() -> enabled().requireInvited("stranger@example.com", true));
    }

    @Test
    void rejectsAnUnverifiedEmailEvenWhenInvited() {
        assertNotInvited(() -> enabled().requireInvited("invited@example.com", false));
    }

    @Test
    void rejectsAPrivateEmail() {
        assertNotInvited(() -> enabled().requireInvited(null, true));
    }

    @Test
    void allowsEveryoneWhenDisabled() {
        EmailAllowlist allowlist = new EmailAllowlist(allowedEmailRepository, false);

        assertThatCode(() -> allowlist.requireInvited(null, false)).doesNotThrowAnyException();
        verifyNoInteractions(allowedEmailRepository);
    }

    private EmailAllowlist enabled() {
        return new EmailAllowlist(allowedEmailRepository, true);
    }

    private void assertNotInvited(Runnable login) {
        OAuth2AuthenticationException exception = catchThrowableOfType(
            OAuth2AuthenticationException.class,
            login::run
        );

        assertThat(exception).isNotNull();
        assertThat(exception.getError().getErrorCode()).isEqualTo(EmailAllowlist.NOT_INVITED);
    }
}
