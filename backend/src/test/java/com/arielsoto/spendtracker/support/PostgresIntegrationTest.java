package com.arielsoto.spendtracker.support;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Base for tests that need a real Postgres. Skipped, instead of failing, when Docker is missing. */
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
public abstract class PostgresIntegrationTest {

	@DynamicPropertySource
	static void registerProperties(DynamicPropertyRegistry registry) {
		PostgreSQLContainer<?> postgres = PostgresContainer.get();
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}
}
