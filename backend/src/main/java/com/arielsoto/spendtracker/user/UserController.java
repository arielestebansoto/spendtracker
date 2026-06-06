package com.arielsoto.spendtracker.user;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.arielsoto.spendtracker.security.AuthenticatedUserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {
    
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/me")
    public UserMeResponse me(
        OAuth2AuthenticationToken authentication
    ) {

        UserApp user =authenticatedUserService
            .getCurrentUser(authentication);

        return new UserMeResponse(
            user.getId(),
            authentication.getPrincipal().getAttribute("name"),
            authentication.getPrincipal().getAttribute("email")
        );
    }
}
