package com.arielsoto.spendtracker.support;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One Postgres for the whole test run: started on first use and stopped by Testcontainers' Ryuk
 * when the JVM exits, so test classes do not each pay for their own database.
 */
public final class PostgresContainer {

	private static PostgreSQLContainer<?> instance;

	private PostgresContainer() {
	}

	public static synchronized PostgreSQLContainer<?> get() {
		if (instance == null) {
			instance = new PostgreSQLContainer<>("postgres:16-alpine");
			instance.start();
		}
		return instance;
	}
}
