package com.arielsoto.spendtracker;

import org.flywaydb.core.Flyway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SpendtrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpendtrackerApplication.class, args);
	}

	@Bean
	CommandLineRunner flywayClean(Flyway flyway) {
		return args -> {
			// flyway.clean();
			// flyway.migrate();
		};
	}
}
