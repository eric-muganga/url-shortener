package com.eric_muganga.url_shortener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;


/**
 * Test configuration that spins up containerized PostgreSQL and Redis
 * for integration tests without needing a local database.
 *
 * Testcontainers automatically:
 * - Pulls the Docker image (first time only)
 * - Starts the container
 * - Exposes ports
 * - Stops the container after tests complete
 *
 * Usage:
 * @SpringBootTest
 * @Import(TestcontainersConfiguration.class)
 * class MyIntegrationTest { }
 */
@TestConfiguration
@Slf4j
class TestcontainersConfiguration {


	/**
	 * PostgreSQL container for integration tests.
	 *
	 * Configuration:
	 * - Image: postgres:16-alpine (lightweight)
	 * - Database: url_shortener_test
	 * - User: test
	 * - Password: test
	 * - Port: 5433 (random, assigned by Testcontainers)
	 *
	 * Spring Boot automatically picks up the mapped port
	 * from the JDBC URL environment variable.
	 */
	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
				.withDatabaseName("url_shortener_test")
				.withUsername("test")
				.withPassword("test")
				.withExposedPorts(5432)
				.waitingFor(
						Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 1)
				);
	}



	/**
	 * Redis container for integration tests.
	 *
	 * Configuration:
	 * - Image: redis:7-alpine (lightweight)
	 * - Port: 6380 (random, assigned by Testcontainers)
	 *
	 * Spring Boot picks up the port from environment variable.
	 */
	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
				.withExposedPorts(6379)
				.waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));
	}

}
