package com.arielsoto.spendtracker.security;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping("/api/v1/security")
public class CsrfController {

    @GetMapping("/csrf")
    // Hidden: Spring Security resolves the token, it is not a request parameter.
    public CsrfToken csrf(@Parameter(hidden = true) CsrfToken token) {
        return token;
    }

}