package com.arielsoto.spendtracker.receipt;

import com.arielsoto.spendtracker.spend.Spend;
import com.arielsoto.spendtracker.spend.SpendRepository;
import com.arielsoto.spendtracker.spend.SpendStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Safety net for the asynchronous pipeline: a Spend that stays PENDING or PROCESSING for longer
 * than the threshold (for example the backend crashed between saving it and queuing it) is marked
 * FAILED, so the user can retry or delete it. Nothing is deleted.
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
    @Transactional
    public void failStuckSpends() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minus(threshold);
        List<Spend> stuckSpends = spendRepository.findAllByStatusInAndStatusChangedAtBefore(IN_PROGRESS, cutoff);

        for (Spend spend : stuckSpends) {
            log.warn("stuck_spend_failed spendId={} status={} statusChangedAt={}",
                spend.getId(), spend.getStatus(), spend.getStatusChangedAt());
            spend.setStatus(SpendStatus.FAILED);
        }
        spendRepository.saveAll(stuckSpends);
    }
}
