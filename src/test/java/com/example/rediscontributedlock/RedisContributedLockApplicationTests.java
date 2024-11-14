package com.example.rediscontributedlock;

import org.assertj.core.util.Sets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@SpringBootTest
class RedisContributedLockApplicationTests {

	private final String URL_1 = "http://localhost:8080/rediscontributedlock/api/getLockByRedissonV1";
	private final String URL_2 = "http://localhost:8080/rediscontributedlock/api/getLockByRedissonV1";

	private final String TRY_LOCK_URL_1 = "http://localhost:8080/rediscontributedlock/api/getTryLockByRedissonV1";
	private final String TRY_LOCK_URL_2 = "http://localhost:8080/rediscontributedlock/api/getTryLockByRedissonV1";

	private final String TRY_FAIR_LOCK_URL_1 = "http://localhost:8080/rediscontributedlock/api/getFairLockByRedissonV1";

	private final String TRY_FAIR_LOCK_URL_2 = "http://localhost:8080/rediscontributedlock/api/getFairLockByRedissonV1";
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
	public void testConcurrentRequestsTryLock() throws InterruptedException {
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
				restTemplate.getForObject(TRY_LOCK_URL_1, String.class);
				latch.countDown();
			}, executorService));
		}


		// Concurrent requests to URL2
		for (int i = 0; i < concurrentThreads; i++) {
			futures.add(CompletableFuture.runAsync(() -> {

				restTemplate.getForObject(TRY_LOCK_URL_2, String.class);
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
	public void testConcurrentRequestsFairLock() throws InterruptedException {
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
				restTemplate.getForObject(TRY_FAIR_LOCK_URL_1, String.class);
				latch.countDown();
			}, executorService));
		}


		// Concurrent requests to URL2
		for (int i = 0; i < concurrentThreads; i++) {
			futures.add(CompletableFuture.runAsync(() -> {

				restTemplate.getForObject(TRY_FAIR_LOCK_URL_2, String.class);
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



	public static void main(String[] args) {
//		輸入: nums = [2, 7, 11, 15], target = 9
//		輸出: [0, 1]
//		解釋: 因為 nums[0] + nums[1] = 2 + 7 = 9

	   int[] nums = {2,7,11,15};

		int [] temp = count(nums, 9);

		System.out.println(temp[0] + " " +  temp[1]);
	}

    public static int[] count(int[] nums, Integer target) {

		HashMap<Integer, Integer> map = new HashMap();

		for (int i = 0; i < nums.length; i++) {

          int result = target-nums[i];

		  if(map.containsKey(result)){

			  return new int[] {map.get(result), i};
		  }

		  map.put(nums[i],i);

		}

		throw new IllegalArgumentException("No two sum solution");
	}


	public static void aaa() {

		List<String> words = Arrays.asList("apple", "banana", "apple", "orange", "banana", "apple");

		words.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));


	}



}
