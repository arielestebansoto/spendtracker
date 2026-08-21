package com.arielsoto.spendtracker.user;

import java.time.LocalDateTime;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.http.ResponseEntity;

import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.spend.SpendService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.security.core.context.SecurityContextHolder;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {
    
    private final AuthenticatedUserService authenticatedUserService;
    private final UserAppRepository userRepository;
    private final SpendService spendService;

    @GetMapping("/me")
    public UserMeResponse me(
        OAuth2AuthenticationToken authentication
    ) {

        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return new UserMeResponse(
            user.getId(),
            authentication.getPrincipal().getAttribute("name"),
            authentication.getPrincipal().getAttribute("email"),
            user.getPrivacyPolicyVersion() != null && user.getTermsVersion() != null
        );
    }

    @GetMapping("/consent")
    public ConsentResponse consent(
        OAuth2AuthenticationToken authentication
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        return new ConsentResponse(
            user.getPrivacyPolicyVersion() != null && user.getTermsVersion() != null,
            user.getPrivacyPolicyVersion(),
            user.getTermsVersion(),
            user.getAcceptedAt()
        );
    }

    @PostMapping("/consent")
    public ConsentResponse recordConsent(
        OAuth2AuthenticationToken authentication,
        @Valid @RequestBody ConsentRequest request
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        user.setPrivacyPolicyVersion(request.privacyPolicyVersion());
        user.setTermsVersion(request.termsVersion());
        user.setAcceptedAt(LocalDateTime.now());

        userRepository.save(user);

        return new ConsentResponse(
            true,
            user.getPrivacyPolicyVersion(),
            user.getTermsVersion(),
            user.getAcceptedAt()
        );
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteAccount(
        OAuth2AuthenticationToken authentication,
        HttpServletRequest request
    ) {
        UserApp user = authenticatedUserService
            .getCurrentUser(authentication);

        spendService.deleteAllByUser(user);

        userRepository.delete(user);

        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        return ResponseEntity.noContent().build();
    }
}
