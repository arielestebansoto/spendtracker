package com.arielsoto.spendtracker.queue;

import java.util.UUID;

/**
 * Body of a queue message. Normally just the Spend id. A category-only message (Reclassify, only
 * on the enrichment queue) is the Spend id followed by ":category-only". Plain id messages keep
 * working unchanged.
 */
public record SpendMessage(UUID spendId, boolean categoryOnly) {

    private static final String CATEGORY_ONLY_SUFFIX = ":category-only";

    public static SpendMessage parse(String body) {
        if (body.endsWith(CATEGORY_ONLY_SUFFIX)) {
            String id = body.substring(0, body.length() - CATEGORY_ONLY_SUFFIX.length());
            return new SpendMessage(UUID.fromString(id), true);
        }
        return new SpendMessage(UUID.fromString(body), false);
    }

    public String toBody() {
        return categoryOnly ? spendId + CATEGORY_ONLY_SUFFIX : spendId.toString();
    }
}
