package com.arielsoto.spendtracker.security;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arielsoto.spendtracker.logging.ClientIpResolver;
import com.arielsoto.spendtracker.logging.SecurityMdcFilter;
import com.arielsoto.spendtracker.user.OAuthProvider;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.user.UserAppRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Logs in a fixed test user without Google or GitHub, so a phone on the same Wi-Fi can use the app
 * locally (docs/local-development.md). Exists only with the dev profile, like its permitAll rule in
 * SecurityConfig.
 */
@Profile("dev")
@RestController
public class DevLoginController {

    static final String PATH = "/dev/login";

    // Stored as a GitHub user with an id real GitHub never gives (its ids are numbers), so no
    // schema change is needed and AuthenticatedUserService resolves it like any GitHub user.
    static final String OAUTH_ID = "dev-tester";

    private static final String REGISTRATION_ID = "github";

    private static final String NAME = "Dev Tester";

    private static final String EMAIL = "dev-tester@example.com";

    private final UserAppRepository userAppRepository;

    private final SecurityAuditLogger auditLogger;

    private final String successUrl;

    private final SecurityContextRepository securityContextRepository =
        new HttpSessionSecurityContextRepository();

    public DevLoginController(
        UserAppRepository userAppRepository,
        SecurityAuditLogger auditLogger,
        @Value("${app.oauth2.default-success-url}") String successUrl
    ) {
        this.userAppRepository = userAppRepository;
        this.auditLogger = auditLogger;
        this.successUrl = successUrl;
    }

    @GetMapping(PATH)
    public void login(HttpServletRequest request, HttpServletResponse response) throws IOException {
        UserApp user = findOrCreateTestUser();

        // The same kind of authentication a GitHub login leaves in the session.
        Map<String, Object> attributes = Map.of("id", OAUTH_ID, "name", NAME, "email", EMAIL);
        OAuth2User principal = new DefaultOAuth2User(
            List.of(new OAuth2UserAuthority(attributes)),
            attributes,
            "id"
        );
        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
            principal,
            principal.getAuthorities(),
            REGISTRATION_ID
        );

        // A new session id on login, as Spring Security does for a real one.
        request.getSession();
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        request.getSession().setAttribute(SecurityMdcFilter.APP_USER_ID, user.getId());

        auditLogger.loginSuccess("dev", user.getId(), ClientIpResolver.resolve(request));

        response.sendRedirect(successUrl);
    }

    private UserApp findOrCreateTestUser() {
        return userAppRepository
            .findByOauthProviderAndOauthId(OAuthProvider.GITHUB, OAUTH_ID)
            .orElseGet(() -> userAppRepository.save(
                UserApp.builder()
                    .oauthProvider(OAuthProvider.GITHUB)
                    .oauthId(OAUTH_ID)
                    .name(NAME)
                    .email(EMAIL)
                    .build()
            ));
    }
}
