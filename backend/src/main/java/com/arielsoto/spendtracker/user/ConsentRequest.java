package com.arielsoto.spendtracker.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConsentRequest(
    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+$", message = "Version must follow semver format (e.g., 1.0)")
    String privacyPolicyVersion,

    @NotBlank
    @Pattern(regexp = "^\\d+\\.\\d+$", message = "Version must follow semver format (e.g., 1.0)")
    String termsVersion
) {}
