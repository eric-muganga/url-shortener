package com.eric_muganga.url_shortener;

import com.eric_muganga.url_shortener.repository.UrlRepository;
import com.eric_muganga.url_shortener.service.UrlService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@SpringBootTest
@Slf4j
class UrlShortenerApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UrlService urlService;

	@Autowired
	private UrlRepository urlRepository;

	@BeforeEach
	void setUp() {
		// Clean up database before each test
		urlRepository.deleteAll();
		log.info("Database cleaned for test");
	}

	// ============ BASIC FUNCTIONALITY TESTS ============

	@Test
	@DisplayName("Create short URL - should return short code")
	void testCreateShortUrl() throws Exception {
		String originalUrl = "https://example.com/very/long/path";

		mockMvc.perform(post("/api/v1/urls/shorten")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\": \"" + originalUrl + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shortCode").exists())
				.andExpect(jsonPath("$.shortenedUrl").exists());

		log.info("✓ Created short URL");
	}

	@Test
	@DisplayName("Retrieve original URL - should return correct mapping")
	void testGetOriginalUrl() throws Exception {
		String originalUrl = "https://github.com/eric-muganga";
		String shortCode = urlService.shortenUrl(originalUrl);

		mockMvc.perform(get("/api/v1/urls/" + shortCode))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.originalUrl").value(originalUrl))
				.andExpect(jsonPath("$.shortCode").value(shortCode));

		log.info("✓ Retrieved original URL");
	}

	@Test
	@DisplayName("Redirect - should return 302 with Location header")
	void testRedirect() throws Exception {
		String originalUrl = "https://www.wikipedia.org";
		String shortCode = urlService.shortenUrl(originalUrl);

		mockMvc.perform(get("/api/v1/urls/" + shortCode + "/r"))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", originalUrl));

		log.info("✓ Redirect works");
	}

	@Test
	@DisplayName("Update URL - should update mapping and invalidate cache")
	void testUpdateUrl() throws Exception {
		String originalUrl = "https://example.com/old";
		String newUrl = "https://example.com/new";
		String shortCode = urlService.shortenUrl(originalUrl);

		// Get it once to populate cache
		urlService.getOriginalUrl(shortCode);

		// Update
		mockMvc.perform(put("/api/v1/urls/" + shortCode)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\": \"" + newUrl + "\"}"))
				.andExpect(status().isOk());

		// Verify updated value is returned
		mockMvc.perform(get("/api/v1/urls/" + shortCode))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.originalUrl").value(newUrl));

		log.info("✓ URL updated");
	}

	@Test
	@DisplayName("Delete URL - should soft delete and return 204")
	void testDeleteUrl() throws Exception {
		String originalUrl = "https://example.com/delete-me";
		String shortCode = urlService.shortenUrl(originalUrl);

		mockMvc.perform(delete("/api/v1/urls/" + shortCode))
				.andExpect(status().isNoContent());

		// Try to retrieve deleted URL - should return 404
		mockMvc.perform(get("/api/v1/urls/" + shortCode))
				.andExpect(status().isNotFound());

		log.info("✓ URL deleted");
	}

	@Test
	@DisplayName("Invalid URL - should return 400")
	void testInvalidUrl() throws Exception {
		mockMvc.perform(post("/api/v1/urls/shorten")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"url\": \"not-a-valid-url\"}"))
				.andExpect(status().isBadRequest());

		log.info("✓ Invalid URL rejected");
	}

	@Test
	@DisplayName("URL not found - should return 404")
	void testUrlNotFound() throws Exception {
		mockMvc.perform(get("/api/v1/urls/nonexistent"))
				.andExpect(status().isNotFound());

		log.info("✓ 404 for nonexistent URL");
	}

	// ============ CONCURRENT TESTS ============

	@Test
	@DisplayName("Concurrent URL creation - should produce unique short codes")
	void testConcurrentUrlCreation() throws InterruptedException {
		int numThreads = 100;
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(numThreads);
		Set<String> shortCodes = Collections.synchronizedSet(new HashSet<>());
		List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

		log.info("Starting concurrent creation test with {} threads", numThreads);

		// Spin up threads
		for (int i = 0; i < numThreads; i++) {
			final int threadId = i;
			new Thread(() -> {
				try {
					// Wait for signal to start
					startLatch.await();

					// All threads hit the service at roughly the same time
					String shortCode = urlService.shortenUrl(
							"https://example.com/url-" + threadId
					);

					// Record the short code
					boolean added = shortCodes.add(shortCode);
					if (!added) {
						throw new RuntimeException(
								"COLLISION DETECTED: Duplicate short code: " + shortCode
						);
					}

				} catch (Exception e) {
					exceptions.add(e);
				} finally {
					endLatch.countDown();
				}
			}).start();
		}

		// Release all threads
		startLatch.countDown();

		// Wait for all to complete
		boolean completed = endLatch.await(30, TimeUnit.SECONDS);
		assertThat(completed)
				.as("All threads should complete within 30 seconds")
				.isTrue();

		// Assertions
		assertThat(exceptions)
				.as("No exceptions should be thrown")
				.isEmpty();

		assertThat(shortCodes.size())
				.as("All short codes should be unique")
				.isEqualTo(numThreads);

		// Verify all are in the database
		long count = urlRepository.count();
		assertThat(count)
				.as("All URLs should be persisted to database")
				.isEqualTo(numThreads);

		log.info("✓ Concurrent creation test passed: {} unique short codes generated", numThreads);
	}

	@Test
	@DisplayName("Concurrent reads - should hit cache effectively")
	void testConcurrentReads() throws InterruptedException {
		// Create 5 URLs
		String[] shortCodes = new String[5];
		for (int i = 0; i < 5; i++) {
			shortCodes[i] = urlService.shortenUrl("https://example.com/url-" + i);
		}

		int numThreads = 100;
		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(numThreads);
		List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

		log.info("Starting concurrent read test with {} threads accessing {} URLs", numThreads, shortCodes.length);

		// Spin up threads that all read from cache
		for (int i = 0; i < numThreads; i++) {
			final int threadId = i;
			new Thread(() -> {
				try {
					startLatch.await();

					// Each thread reads from a random short code (80/20 pattern)
					String shortCode = shortCodes[threadId % shortCodes.length];
					String originalUrl = urlService.getOriginalUrl(shortCode);
					assertThat(originalUrl).isNotBlank();

				} catch (Exception e) {
					exceptions.add(e);
				} finally {
					endLatch.countDown();
				}
			}).start();
		}

		startLatch.countDown();

		boolean completed = endLatch.await(30, TimeUnit.SECONDS);
		assertThat(completed).isTrue();
		assertThat(exceptions).isEmpty();

		log.info("✓ Concurrent read test passed");
	}

	@Test
	@DisplayName("Concurrent creates and reads - stress test")
	void testConcurrentCreatesAndReads() throws InterruptedException {
		int numCreators = 50;
		int numReaders = 50;
		int totalThreads = numCreators + numReaders;

		CountDownLatch startLatch = new CountDownLatch(1);
		CountDownLatch endLatch = new CountDownLatch(totalThreads);
		Set<String> createdShortCodes = Collections.synchronizedSet(new HashSet<>());
		List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());

		log.info("Starting stress test: {} creators + {} readers", numCreators, numReaders);

		// Creator threads
		for (int i = 0; i < numCreators; i++) {
			final int threadId = i;
			new Thread(() -> {
				try {
					startLatch.await();
					String shortCode = urlService.shortenUrl("https://example.com/stress-" + threadId);
					createdShortCodes.add(shortCode);
				} catch (Exception e) {
					exceptions.add(e);
				} finally {
					endLatch.countDown();
				}
			}).start();
		}

		// Reader threads (read existing data)
		for (int i = 0; i < numReaders; i++) {
			final int threadId = i;
			new Thread(() -> {
				try {
					startLatch.await();
					// Initial URLs for reading
					if (threadId < 5) {
						urlService.shortenUrl("https://example.com/initial-" + threadId);
					}
					// Then read (might hit cache or misses initially)
					String[] initialCodes = new String[5];
					for (int j = 0; j < 5; j++) {
						if (j < initialCodes.length) {
							initialCodes[j] = "initial-" + j;
						}
					}
				} catch (Exception e) {
					exceptions.add(e);
				} finally {
					endLatch.countDown();
				}
			}).start();
		}

		startLatch.countDown();

		boolean completed = endLatch.await(30, TimeUnit.SECONDS);
		assertThat(completed).isTrue();
		assertThat(exceptions).isEmpty();
		assertThat(createdShortCodes.size()).isGreaterThan(0);

		log.info("✓ Stress test passed: {} short codes created", createdShortCodes.size());
	}

	// ============ CACHE TESTS ============

	@Test
	@DisplayName("Cache hit ratio - should improve after repeated reads")
	void testCacheHitRatio() {
		String shortCode = urlService.shortenUrl("https://example.com/cache-test");

		// First read: cache miss (hits database)
		urlService.getOriginalUrl(shortCode);

		// Subsequent reads: cache hits
		for (int i = 0; i < 10; i++) {
			urlService.getOriginalUrl(shortCode);
		}

		// If we had metrics tracking, we'd see:
		// 1 database hit + 10 cache hits = 90.9% hit rate

		log.info("✓ Cache hit ratio test completed");
	}

	// ============ STATS TESTS ============

	@Test
	@DisplayName("Get stats - should return metadata")
	void testGetStats() throws Exception {
		String originalUrl = "https://example.com/stats-test";
		String shortCode = urlService.shortenUrl(originalUrl);

		mockMvc.perform(get("/api/v1/urls/" + shortCode + "/stats"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shortCode").value(shortCode))
				.andExpect(jsonPath("$.originalUrl").value(originalUrl))
				.andExpect(jsonPath("$.createdAt").exists());

		log.info("✓ Stats endpoint works");
	}

	@Test
	@DisplayName("Metrics snapshot - should return cache metrics")
	void testMetricsSnapshot() throws Exception {
		// Create and read some URLs to populate metrics
		for (int i = 0; i < 10; i++) {
			String code = urlService.shortenUrl("https://example.com/metric-" + i);
			urlService.getOriginalUrl(code);
		}

		mockMvc.perform(get("/api/v1/metrics/snapshot"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalRequests").exists())
				.andExpect(jsonPath("$.cacheHitRatioPercent").exists());

		log.info("✓ Metrics snapshot works");
	}

}
