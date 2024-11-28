package com.turbospaces.boot;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// Implement method in allowRequest that will work like a rate limiter based on userId.
public class Task2 {
    private static final ExecutorService WORKER = Executors.newFixedThreadPool(5);

    public static void main(String[] args) throws Exception {
        long now = 0; // 00:00
        RateLimiter rateLimiter = new RateLimiter(5, now); // 5 requests per minute per user

        String testUser1 = "user42";
        String testUser2 = "user43";

        sendRequestsInParallel(() -> {assert rateLimiter.allowRequest(testUser1);}, 3);
        rateLimiter.setNow(now + TimeUnit.SECONDS.toMillis(58));  // 00:59
        sendRequestsInParallel(() -> {assert rateLimiter.allowRequest(testUser1);}, 2);
        sendRequestsInParallel(() -> {assert rateLimiter.allowRequest(testUser2);}, 4);
        sendRequestsInParallel(() -> {assert !rateLimiter.allowRequest(testUser1);}, 5);

        // minute passed new request should be allowed
        rateLimiter.setNow(rateLimiter.currentTimestamp + TimeUnit.SECONDS.toMillis(61)); // 01:59
        sendRequestsInParallel(() -> {assert rateLimiter.allowRequest(testUser1);}, 5);

        // another minute passed, allow but ...
        rateLimiter.setNow(now + TimeUnit.SECONDS.toMillis(5)); // 02:05
        sendRequestsInParallel(() -> {assert !rateLimiter.allowRequest(testUser1);}, 5);

        WORKER.shutdown();
        System.out.println("All tests passed");
    }

    public static void sendRequestsInParallel(Runnable request, int times) throws Exception {
        var results = new ArrayList<Future>();
        for (int i = 0; i < times; i++) {
            results.add(WORKER.submit(request));
        }
        for (Future result : results) {
            result.get();
        }
    }

    public static class RateLimiter {
        public long currentTimestamp;
        public int limitPerMinute;

        public RateLimiter(int limitPerSecond, long now) {
            this.limitPerMinute = limitPerSecond;
            this.currentTimestamp = now;
        }

        // actual call of request with rate limiting logic
        public boolean allowRequest(String identifier) {
            return true;
        }

        public void setNow(long now) {
            this.currentTimestamp = now;
        }
    }
}
