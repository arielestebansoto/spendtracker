package com.arielsoto.spendtracker.aiusage;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arielsoto.spendtracker.aiusage.dto.AiUsageResponse;
import com.arielsoto.spendtracker.security.AuthenticatedUserService;
import com.arielsoto.spendtracker.user.UserApp;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/ai-usage")
@RequiredArgsConstructor
public class AiUsageController {

    private final AiUsageService aiUsageService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/me")
    public AiUsageResponse getMyUsage(OAuth2AuthenticationToken authentication) {
        UserApp user = authenticatedUserService.getCurrentUser(authentication);

        return aiUsageService.getUserUsage(user);
    }
}
