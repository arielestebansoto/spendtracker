package com.arielsoto.spendtracker;

import com.arielsoto.spendtracker.support.PostgresContainer;
import com.arielsoto.spendtracker.support.PostgresIntegrationTest;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the Flyway migrations in their own schemas, apart from the schema the Spring tests use. */
class MigrationIT extends PostgresIntegrationTest {

	@Test
	void migrationsApplyOnAnEmptyDatabase() {
		Flyway flyway = flyway("migration_empty");

		flyway.migrate();

		assertThat(flyway.info().pending()).isEmpty();
	}

	@Test
	void v12MarksExistingProcessingSpendsAsEnrichment() throws Exception {
		Flyway beforeV12 = flywayTo("migration_existing", "11");
		beforeV12.migrate();
		insertSpends("migration_existing");

		flyway("migration_existing").migrate();

		assertThat(stages("migration_existing")).containsExactlyInAnyOrder("PROCESSING:ENRICHMENT", "PENDING:null");
	}

	private Flyway flyway(String schema) {
		return flywayTo(schema, "latest");
	}

	private Flyway flywayTo(String schema, String target) {
		PostgreSQLContainer<?> postgres = PostgresContainer.get();
		return Flyway.configure()
			.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
			.schemas(schema)
			.target(target)
			.cleanDisabled(false)
			.load();
	}

	private void insertSpends(String schema) throws Exception {
		try (Connection c = connection(); Statement s = c.createStatement()) {
			s.execute("SET search_path TO " + schema);
			s.execute("INSERT INTO users (id, oauth_provider, oauth_id, name) "
				+ "VALUES ('00000000-0000-0000-0000-000000000001', 'GITHUB', 'x', 'Test')");
			String category = "(SELECT id FROM categories LIMIT 1)";
			for (String status : List.of("PROCESSING", "PENDING")) {
				s.execute("INSERT INTO spends (id, user_id, category_id, amount, spend_date, status, status_changed_at) "
					+ "VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', " + category
					+ ", 1, now(), '" + status + "', now())");
			}
		}
	}

	private List<String> stages(String schema) throws Exception {
		List<String> result = new ArrayList<>();
		try (Connection c = connection(); Statement s = c.createStatement()) {
			s.execute("SET search_path TO " + schema);
			ResultSet rs = s.executeQuery("SELECT status, processing_stage FROM spends");
			while (rs.next()) {
				result.add(rs.getString(1) + ":" + rs.getString(2));
			}
		}
		return result;
	}

	private Connection connection() throws Exception {
		PostgreSQLContainer<?> postgres = PostgresContainer.get();
		return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
	}
}
