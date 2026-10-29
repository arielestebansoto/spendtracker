package com.arielsoto.spendtracker.spend;

import com.arielsoto.spendtracker.category.Category;
import com.arielsoto.spendtracker.category.CategoryRepository;
import com.arielsoto.spendtracker.receipt.ReceiptMetadata;
import com.arielsoto.spendtracker.receipt.ReceiptMetadataRepository;
import com.arielsoto.spendtracker.support.PostgresIntegrationTest;
import com.arielsoto.spendtracker.user.OAuthProvider;
import com.arielsoto.spendtracker.user.UserApp;
import com.arielsoto.spendtracker.user.UserAppRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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

	@Autowired SpendRepository spendRepository;
	@Autowired ReceiptMetadataRepository receiptMetadataRepository;
	@Autowired UserAppRepository userRepository;
	@Autowired CategoryRepository categoryRepository;
	@Autowired EntityManager entityManager;

	@Test
	void claimForExtractionClaimsAPendingSpend() {
		Spend spend = saveSpend(SpendStatus.PENDING, null);

		assertThat(spendRepository.claimForExtraction(spend.getId())).isEqualTo(1);

		Spend claimed = reload(spend);
		assertThat(claimed.getStatus()).isEqualTo(SpendStatus.PROCESSING);
		assertThat(claimed.getProcessingStage()).isEqualTo(ProcessingStage.EXTRACTION);
	}

	@Test
	void claimForExtractionClaimsASpendInterruptedDuringExtraction() {
		Spend spend = saveSpend(SpendStatus.PROCESSING, ProcessingStage.EXTRACTION);

		assertThat(spendRepository.claimForExtraction(spend.getId())).isEqualTo(1);
	}

	@Test
	void claimForExtractionIgnoresSpendsInOtherStates() {
		Spend enrichment = saveSpend(SpendStatus.PROCESSING, ProcessingStage.ENRICHMENT);
		Spend processed = saveSpend(SpendStatus.PROCESSED, null);
		Spend failed = saveSpend(SpendStatus.FAILED, null);

		assertThat(spendRepository.claimForExtraction(enrichment.getId())).isZero();
		assertThat(spendRepository.claimForExtraction(processed.getId())).isZero();
		assertThat(spendRepository.claimForExtraction(failed.getId())).isZero();
		assertThat(spendRepository.claimForExtraction(UUID.randomUUID())).isZero();
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
	@Disabled("claimForExtraction cannot tell a live worker from a dead one, see #106")
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
				return spendRepository.claimForExtraction(id);
			};
			Future<Integer> first = pool.submit(claim);
			Future<Integer> second = pool.submit(claim);
			start.countDown();
			return first.get() + second.get();
		} finally {
			pool.shutdownNow();
		}
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
