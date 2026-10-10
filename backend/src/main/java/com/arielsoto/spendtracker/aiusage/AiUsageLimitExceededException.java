package com.arielsoto.spendtracker.aiusage;

public class AiUsageLimitExceededException extends RuntimeException {

    private final String resourceType;

    public AiUsageLimitExceededException(String resourceType, String message) {
        super(message);
        this.resourceType = resourceType;
    }

    public String getResourceType() {
        return resourceType;
    }
}
