package com.arielsoto.spendtracker.spend;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.receipt.ReceiptMetadata;
import com.arielsoto.spendtracker.receipt.ReceiptMetadataRepository;
import com.arielsoto.spendtracker.support.PostgresIntegrationTest;
import com.arielsoto.spendtracker.user.OAuthProvider;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.user.UserAppRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class SpendRepositoryIT extends PostgresIntegrationTest {

	private static final Duration LEASE = Duration.ofSeconds(60);
	private static final UUID FIRST_TOKEN = UUID.randomUUID();
	private static final UUID SECOND_TOKEN = UUID.randomUUID();

	@Autowired SpendRepository spendRepository;
	@Autowired ReceiptMetadataRepository receiptMetadataRepository;
	@Autowired UserAppRepository userRepository;
	@Autowired CategoryRepository categoryRepository;
	@Autowired EntityManager entityManager;

	@Test
	void claimForExtractionClaimsAPendingSpend() {
		Spend spend = saveSpend(SpendStatus.PENDING, null);

		assertThat(claim(spend.getId())).isEqualTo(1);

		Spend claimed = reload(spend);
		assertThat(claimed.getStatus()).isEqualTo(SpendStatus.PROCESSING);
		assertThat(claimed.getProcessingStage()).isEqualTo(ProcessingStage.EXTRACTION);
	}

	@Test
	void claimForExtractionResumesASpendWhoseLeaseExpired() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.EXTRACTION);
		backdateStatusChange(spend, LEASE.plusSeconds(1));

		assertThat(claim(spend.getId())).isEqualTo(1);
	}

	@Test
	void claimForExtractionLeavesASpendWithALiveLeaseToItsWorker() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.EXTRACTION);
		backdateStatusChange(spend, LEASE.minusSeconds(10));

		assertThat(claim(spend.getId())).isZero();
	}

	@Test
	void claimForExtractionRenewsTheLease() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.EXTRACTION);
		backdateStatusChange(spend, LEASE.plusSeconds(1));

		claim(spend.getId());

		assertThat(claim(spend.getId())).isZero();
	}

	@Test
	void claimForExtractionIgnoresSpendsInOtherStates() {
		Spend enrichment = saveSpend(SpendStatus.PROCESSING, ProcessingStage.ENRICHMENT);
		Spend processed = saveSpend(SpendStatus.PROCESSED, null);
		Spend failed = saveSpend(SpendStatus.FAILED, null);

		assertThat(claim(enrichment.getId())).isZero();
		assertThat(claim(processed.getId())).isZero();
		assertThat(claim(failed.getId())).isZero();
		assertThat(claim(UUID.randomUUID())).isZero();
	}

	@Test
	void aDisplacedWorkerCannotAdvanceTheStage() {
		Spend spend = spendTakenOverBySecondWorker();

		assertThat(spendRepository.advanceToEnrichment(spend.getId(), FIRST_TOKEN, LocalDateTime.now())).isZero();

		assertThat(reload(spend).getProcessingStage()).isEqualTo(ProcessingStage.EXTRACTION);
	}

	@Test
	void aDisplacedWorkerCannotFailOrReleaseTheSpend() {
		Spend spend = spendTakenOverBySecondWorker();

		int failed = spendRepository.releaseExtraction(spend.getId(), FIRST_TOKEN, SpendStatus.FAILED, LocalDateTime.now());
		int pending = spendRepository.releaseExtraction(spend.getId(), FIRST_TOKEN, SpendStatus.PENDING, LocalDateTime.now());

		assertThat(failed).isZero();
		assertThat(pending).isZero();
		Spend current = reload(spend);
		assertThat(current.getStatus()).isEqualTo(SpendStatus.PROCESSING);
		assertThat(current.getProcessingStage()).isEqualTo(ProcessingStage.EXTRACTION);
	}

	@Test
	void theCurrentOwnerCanAdvanceTheStageAndLeavesTheSpendUnclaimed() {
		Spend spend = spendTakenOverBySecondWorker();

		assertThat(spendRepository.advanceToEnrichment(spend.getId(), SECOND_TOKEN, LocalDateTime.now())).isEqualTo(1);

		Spend advanced = reload(spend);
		assertThat(advanced.getProcessingStage()).isEqualTo(ProcessingStage.ENRICHMENT);
		assertThat(advanced.getClaimToken()).isNull();
	}

	@Test
	void claimForEnrichmentClaimsAnUnclaimedSpendInEnrichment() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.ENRICHMENT);

		assertThat(claimForEnrichment(spend.getId(), FIRST_TOKEN)).isEqualTo(1);

		assertThat(reload(spend).getClaimToken()).isEqualTo(FIRST_TOKEN);
	}

	@Test
	void claimForEnrichmentLeavesAClaimedSpendWithALiveLeaseToItsWorker() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.ENRICHMENT);
		claimForEnrichment(spend.getId(), FIRST_TOKEN);

		assertThat(claimForEnrichment(spend.getId(), SECOND_TOKEN)).isZero();
	}

	@Test
	void claimForEnrichmentResumesASpendWhoseLeaseExpired() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.ENRICHMENT);
		claimForEnrichment(spend.getId(), FIRST_TOKEN);
		backdateStatusChange(spend, LEASE.plusSeconds(1));

		assertThat(claimForEnrichment(spend.getId(), SECOND_TOKEN)).isEqualTo(1);
	}

	@Test
	void claimForEnrichmentIgnoresSpendsInOtherStates() {
		Spend extraction = saveSpend(SpendStatus.PROCESSING, ProcessingStage.EXTRACTION);
		Spend pending = saveSpend(SpendStatus.PENDING, null);
		Spend processed = saveSpend(SpendStatus.PROCESSED, null);

		assertThat(claimForEnrichment(extraction.getId(), FIRST_TOKEN)).isZero();
		assertThat(claimForEnrichment(pending.getId(), FIRST_TOKEN)).isZero();
		assertThat(claimForEnrichment(processed.getId(), FIRST_TOKEN)).isZero();
	}

	@Test
	void aDisplacedEnrichmentWorkerCannotConfirmOrReleaseItsClaim() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.ENRICHMENT);
		claimForEnrichment(spend.getId(), FIRST_TOKEN);
		backdateStatusChange(spend, LEASE.plusSeconds(1));
		claimForEnrichment(spend.getId(), SECOND_TOKEN);

		assertThat(spendRepository.confirmClaim(spend.getId(), FIRST_TOKEN, LocalDateTime.now())).isZero();
		assertThat(spendRepository.releaseEnrichment(spend.getId(), FIRST_TOKEN)).isZero();

		assertThat(reload(spend).getClaimToken()).isEqualTo(SECOND_TOKEN);
	}

	@Test
	void theCurrentEnrichmentOwnerCanConfirmAndReleaseItsClaim() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.ENRICHMENT);
		claimForEnrichment(spend.getId(), FIRST_TOKEN);

		assertThat(spendRepository.confirmClaim(spend.getId(), FIRST_TOKEN, LocalDateTime.now())).isEqualTo(1);
		assertThat(spendRepository.releaseEnrichment(spend.getId(), FIRST_TOKEN)).isEqualTo(1);

		Spend released = reload(spend);
		assertThat(released.getClaimToken()).isNull();
		assertThat(released.getStatus()).isEqualTo(SpendStatus.PROCESSING);
	}

	@Test
	void theCurrentOwnerCanReleaseTheSpend() {
		Spend spend = spendTakenOverBySecondWorker();

		assertThat(spendRepository.releaseExtraction(spend.getId(), SECOND_TOKEN, SpendStatus.PENDING, LocalDateTime.now()))
			.isEqualTo(1);

		Spend released = reload(spend);
		assertThat(released.getStatus()).isEqualTo(SpendStatus.PENDING);
		assertThat(released.getProcessingStage()).isNull();
	}

	// The first worker claims, stalls past the lease, and the second worker claims the same Spend.
	@Test
	void countsTheUsersReceiptUploadsCreatedAfterTheGivenTime() {
		UserApp owner = saveSpendWithReceipt(newSpend(SpendStatus.PROCESSED, null), "spends/a.jpg").getUser();
		saveSpendWithReceipt(spendOf(owner), "spends/b.pdf");
		saveSpendWithReceipt(spendOf(owner), null);
		Spend old = saveSpendWithReceipt(spendOf(owner), "spends/c.png");
		saveSpendWithReceipt(newSpend(SpendStatus.PROCESSED, null), "spends/other-user.jpg");
		entityManager.createQuery("UPDATE Spend s SET s.createdAt = :at WHERE s.id = :id")
			.setParameter("at", LocalDateTime.now().minusDays(2))
			.setParameter("id", old.getId())
			.executeUpdate();
		entityManager.clear();

		long uploads = spendRepository.countByUserIdAndReceiptKeyIsNotNullAndCreatedAtAfter(
			owner.getId(), LocalDateTime.now().minusDays(1));

		// The manual Spend, the old upload and the other user's upload are not counted.
		assertThat(uploads).isEqualTo(2);
	}

	private Spend saveSpendWithReceipt(Spend spend, String receiptKey) {
		spend.setReceiptKey(receiptKey);
		Spend saved = spendRepository.save(spend);
		entityManager.flush();
		return saved;
	}

	private Spend spendOf(UserApp owner) {
		Spend spend = newSpend(SpendStatus.PROCESSED, null);
		spend.setUser(owner);
		return spend;
	}

	private Spend spendTakenOverBySecondWorker() {
		Spend spend = saveSpend(SpendStatus.PENDING, null);
		claim(spend.getId(), FIRST_TOKEN);
		backdateStatusChange(spend, LEASE.plusSeconds(1));
		claim(spend.getId(), SECOND_TOKEN);
		return spend;
	}

	@Test
	void savingASpendKeepsReceiptMetadataStoredEarlier() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.EXTRACTION);
		ReceiptMetadata metadata = ReceiptMetadata.builder()
			.spend(spend)
			.rawOcrText("TOTAL 10.00")
			.createdAt(LocalDateTime.now())
			.build();
		receiptMetadataRepository.save(metadata);
		spend.setReceiptMetadata(metadata);

		spendRepository.save(spend);
		entityManager.flush();
		entityManager.clear();

		assertThat(receiptMetadataRepository.findBySpendId(spend.getId())).isPresent();
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void onlyOneOfTwoConcurrentClaimsWins() throws Exception {
		Spend spend = spendRepository.saveAndFlush(newSpend(SpendStatus.PENDING, null));
		try {
			int wins = concurrentClaims(spend.getId(), 2);

			assertThat(wins).isEqualTo(1);
		} finally {
			spendRepository.deleteById(spend.getId());
			userRepository.deleteById(spend.getUser().getId());
		}
	}

	private int concurrentClaims(UUID id, int workers) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(workers);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Callable<Integer> claim = () -> {
				start.await();
				return claim(id);
			};
			Future<Integer> first = pool.submit(claim);
			Future<Integer> second = pool.submit(claim);
			start.countDown();
			return first.get() + second.get();
		} finally {
			pool.shutdownNow();
		}
	}

	private int claim(UUID id) {
		return claim(id, UUID.randomUUID());
	}

	private int claim(UUID id, UUID claimToken) {
		LocalDateTime now = LocalDateTime.now();
		return spendRepository.claimForExtraction(id, claimToken, now, now.minus(LEASE));
	}

	private int claimForEnrichment(UUID id, UUID claimToken) {
		LocalDateTime now = LocalDateTime.now();
		return spendRepository.claimForEnrichment(id, claimToken, now, now.minus(LEASE));
	}

	private void backdateStatusChange(Spend spend, Duration age) {
		entityManager.createQuery("UPDATE Spend s SET s.statusChangedAt = :at WHERE s.id = :id")
			.setParameter("at", LocalDateTime.now().minus(age))
			.setParameter("id", spend.getId())
			.executeUpdate();
		entityManager.clear();
	}

	private Spend saveSpend(SpendStatus status, ProcessingStage stage) {
		Spend spend = spendRepository.save(newSpend(status, stage));
		entityManager.flush();
		entityManager.clear();
		return spend;
	}

	private Spend reload(Spend spend) {
		entityManager.clear();
		return spendRepository.findById(spend.getId()).orElseThrow();
	}

	private Spend newSpend(SpendStatus status, ProcessingStage stage) {
		UserApp user = userRepository.save(UserApp.builder()
			.oauthProvider(OAuthProvider.GITHUB)
			.oauthId(UUID.randomUUID().toString())
			.name("Test User")
			.build());
		Category category = categoryRepository.findAll().get(0);
		Spend spend = Spend.builder()
			.user(user)
			.category(category)
			.amount(new BigDecimal("10.00"))
			.spendDate(LocalDate.now())
			.status(status)
			.statusChangedAt(LocalDateTime.now())
			.processingStage(stage)
			.build();
		return spend;
	}
}
