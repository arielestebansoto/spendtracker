package com.arielsoto.spendtracker.user;

import java.util.UUID;

public record UserMeResponse(
    UUID id,
    String name,
    String email,
    boolean hasAcceptedPolicies
) {

}
