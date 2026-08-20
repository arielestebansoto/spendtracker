package com.arielsoto.spendtracker.user;

import java.time.LocalDateTime;

public record ConsentResponse(
    boolean hasAcceptedPolicies,
    String privacyPolicyVersion,
    String termsVersion,
    LocalDateTime acceptedAt
) {}
