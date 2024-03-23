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

	private final String URL_1 = "http://localhost:8080/rediscontributedlock/api/getLockByRedissonV1";
	private final String URL_2 = "http://localhost:8080/rediscontributedlock/api/getLockByRedissonV1";
	private final RestTemplate restTemplate = new RestTemplate();
	private final String TICKETS_QUANTITY_URL = "http://localhost:8080/grabTickets/api/setTicketsQuantity";
	private final String TICKET_URL = "http://localhost:8080/grabTickets/api/getTicket";

	private final String INSTALL_RED_ENVELOPES_URL = "http://localhost:8080/redEnvelope/api/installRedEnvelopes";

	private final String GRAB_RED_ENVELOPES_URL = "http://localhost:8080/redEnvelope/api/grabRedEnvelope";
	@Test
	public void testConcurrentRequests() throws InterruptedException {
		int concurrentThreads = 30;
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
				restTemplate.getForObject(URL_1, String.class);
				latch.countDown();
			}, executorService));
		}


		// Concurrent requests to URL2
		for (int i = 0; i < concurrentThreads; i++) {
			futures.add(CompletableFuture.runAsync(() -> {

				restTemplate.getForObject(URL_2, String.class);
				latch.countDown();

			}, executorService));
		}

		// Wait for all requests to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// Shutdown the executor service
		executorService.shutdown();
		executorService.shutdown();
	}


	@Test
	public void setTicketsQuantity()  {
		restTemplate.getForObject(TICKETS_QUANTITY_URL, String.class);

	}

	@Test
	public void testGrabTickets() throws InterruptedException {
		int concurrentThreads = 7;
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
				restTemplate.getForObject(TICKET_URL, String.class);
				latch.countDown();
			}, executorService));
		}


		// Concurrent requests to URL2
		for (int i = 0; i < concurrentThreads; i++) {
			futures.add(CompletableFuture.runAsync(() -> {

				restTemplate.getForObject(TICKET_URL, String.class);
				latch.countDown();

			}, executorService));
		}

		// Wait for all requests to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// Shutdown the executor service
		executorService.shutdown();
		executorService.shutdown();
	}

	@Test
	public void installRedEnvelopes()  {
		restTemplate.getForObject(INSTALL_RED_ENVELOPES_URL, String.class);

	}

	@Test
	public void testGrabRedEnvelopes() throws InterruptedException {
		int concurrentThreads = 7;
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
				restTemplate.getForObject(GRAB_RED_ENVELOPES_URL, String.class);
				latch.countDown();
			}, executorService));
		}


		// Concurrent requests to URL2
		for (int i = 0; i < concurrentThreads; i++) {
			futures.add(CompletableFuture.runAsync(() -> {

				restTemplate.getForObject(GRAB_RED_ENVELOPES_URL, String.class);
				latch.countDown();

			}, executorService));
		}

		// Wait for all requests to complete
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		// Shutdown the executor service
		executorService.shutdown();
		executorService.shutdown();
	}


}
