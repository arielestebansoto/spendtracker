package com.arielsoto.spendtracker.receipt;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.user.UserApp;

import lombok.RequiredArgsConstructor;

/**
 * Per-user limit on Receipt uploads over the last minute and the last day.
 *
 * The AI quota is only checked at upload and recorded later by the queue workers, so without this a
 * burst of uploads could all pass the quota check before any usage is recorded. nginx limits per IP;
 * this limits per user.
 *
 * Uploads are counted from the user's Spends that have a Receipt, by creation time. Known gaps, accepted
 * for simplicity: deleting a Spend frees its slot, and concurrent uploads can each see the count below
 * the limit (nginx bounds that burst). Retry and reclassify are not counted: the Spend status and the AI
 * quota already bound them.
 */
@Service
@EnableConfigurationProperties(ReceiptUploadLimitProperties.class)
@RequiredArgsConstructor
public class ReceiptUploadRateLimiter {

    private final SpendRepository spendRepository;
    private final ReceiptUploadLimitProperties limits;
    private final Clock clock;

    /** Throws {@link ReceiptUploadRateLimitException} if the user has no uploads left in either window. */
    public void check(UserApp user) {
        LocalDateTime now = LocalDateTime.now(clock);

        if (uploadsSince(user, now.minusMinutes(1)) >= limits.perMinute()) {
            throw new ReceiptUploadRateLimitException(
                "minute", "Too many receipt uploads. Try again in a minute."
            );
        }
        if (uploadsSince(user, now.minusDays(1)) >= limits.perDay()) {
            throw new ReceiptUploadRateLimitException(
                "day", "Daily receipt upload limit reached. Try again tomorrow."
            );
        }
    }

    private long uploadsSince(UserApp user, LocalDateTime since) {
        return spendRepository.countByUserIdAndReceiptKeyIsNotNullAndCreatedAtAfter(user.getId(), since);
    }
}
