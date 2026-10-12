package com.arielsoto.spendtracker.classificationrule;

public class RuleLimitExceededException extends RuntimeException {

    public RuleLimitExceededException(String message) {
        super(message);
    }
}
