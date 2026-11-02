package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.spend.SpendStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety net for the asynchronous pipeline: a Spend that stays PENDING or PROCESSING for longer
 * than the threshold (for example the backend crashed between saving it and queuing it, or its
 * message reached the DLQ without the worker seeing the last attempt) is marked FAILED, so the user
 * can retry or delete it. Nothing is deleted.
 *
 * Every claim, release and stage change refreshes statusChangedAt, so a live Spend is never older
 * than about one visibility timeout plus one stage. Each Spend is failed with a conditional update,
 * so one that a worker finished in the meantime is not overwritten, and a worker still running on it
 * loses its claim and writes nothing more.
 */
@Service
@Slf4j
public class StuckSpendCleanupService {

    private static final List<SpendStatus> IN_PROGRESS = List.of(SpendStatus.PENDING, SpendStatus.PROCESSING);

    private final SpendRepository spendRepository;
    private final Clock clock;
    private final Duration threshold;

    public StuckSpendCleanupService(
            SpendRepository spendRepository,
            Clock clock,
            @Value("${app.stuck-spend-cleanup.threshold}") Duration threshold) {
        this.spendRepository = spendRepository;
        this.clock = clock;
        this.threshold = threshold;
    }

    @Scheduled(fixedDelayString = "${app.stuck-spend-cleanup.interval}")
    public void failStuckSpends() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime cutoff = now.minus(threshold);
        List<Spend> candidates = spendRepository.findAllByStatusInAndStatusChangedAtBefore(IN_PROGRESS, cutoff);

        for (Spend spend : candidates) {
            if (spendRepository.failIfStuck(spend.getId(), cutoff, now) == 1) {
                log.warn("stuck_spend_failed spendId={} status={} statusChangedAt={}",
                    spend.getId(), spend.getStatus(), spend.getStatusChangedAt());
            }
        }
    }
}
