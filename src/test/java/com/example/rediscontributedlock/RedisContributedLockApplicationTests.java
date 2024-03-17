package com.example.rediscontributedlock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class RedisContributedLockApplicationTests {

	private final String url1 = "http://localhost:8080/ticketGrabber/api/getLockByRedissonV1";
	private final String url2 = "http://localhost:8080/ticketGrabber/api/getLockByRedissonV1";
	private final int concurrentThreads = 30;

	private final RestTemplate restTemplate = new RestTemplate();
	@Test
	public void testConcurrentRequests() throws InterruptedException {
		ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads * 2);
		CountDownLatch latch = new CountDownLatch(concurrentThreads * 2);

		List<CompletableFuture<Void>> futures = new ArrayList<>();

		// Concurrent requests to URL1
		for (int i = 0; i < concurrentThreads; i++) {
			futures.add(CompletableFuture.runAsync(() -> {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				restTemplate.getForObject(url1, String.class);
				latch.countDown();
			}, executorService));
		}


		// Concurrent requests to URL2
		for (int i = 0; i < concurrentThreads; i++) {
			futures.add(CompletableFuture.runAsync(() -> {

				restTemplate.getForObject(url2, String.class);
				latch.countDown();

			}, executorService));
		}

		// Wait for all requests to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// Shutdown the executor service
		executorService.shutdown();
		executorService.shutdown();
	}

	public void ConcurrentRequestTest() {
		// Configure a connection pool for the RestTemplate
		((SimpleClientHttpRequestFactory) restTemplate.getRequestFactory()).setConnectTimeout(5000); // 5 seconds
		((SimpleClientHttpRequestFactory) restTemplate.getRequestFactory()).setReadTimeout(5000); // 5 seconds
	}

}
