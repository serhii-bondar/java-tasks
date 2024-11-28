package com.turbospaces.boot;

import java.util.concurrent.TimeUnit;

// Implement method in allowRequest that will work like a rate limiter based on userId.
public class Task2 {
    public static void main(String[] args) throws Exception {
        long now = 0; // 00:00
        RateLimiter rateLimiter = new RateLimiter(5, now); // 5 requests per minute per user

        String testUser1 = "user42";
        String testUser2 = "user43";

        assert rateLimiter.allowRequest(testUser1);
        assert rateLimiter.allowRequest(testUser1);
        assert rateLimiter.allowRequest(testUser1);
        rateLimiter.setNow(now + TimeUnit.SECONDS.toMillis(59));  // 00:59
        assert rateLimiter.allowRequest(testUser1);
        assert rateLimiter.allowRequest(testUser1);
        assert rateLimiter.allowRequest(testUser2);
        assert !rateLimiter.allowRequest(testUser1);

        // minute passed new request should be allowed
        rateLimiter.setNow(now + TimeUnit.MINUTES.toMillis(1)); // 01:59
        assert rateLimiter.allowRequest(testUser1);
        assert rateLimiter.allowRequest(testUser1);
        assert rateLimiter.allowRequest(testUser1);
        assert rateLimiter.allowRequest(testUser1);
        assert rateLimiter.allowRequest(testUser1);

        // another minute passed, allow but ...
        rateLimiter.setNow(now + TimeUnit.SECONDS.toMillis(5)); // 02:05
        assert !rateLimiter.allowRequest(testUser1);
        assert !rateLimiter.allowRequest(testUser1);
        assert !rateLimiter.allowRequest(testUser1);
        assert !rateLimiter.allowRequest(testUser1);
        assert !rateLimiter.allowRequest(testUser1);
    }

    public static class RateLimiter {
        public long currentTimestamp;
        public int limitPerSecond;

        public RateLimiter(int limitPerSecond, long now) {
            this.limitPerSecond = limitPerSecond;
        }

        // actual call of request with rate limiting logic
        public boolean allowRequest(String identifier) {
            return false;
        }

        public void setNow(long now) {
            this.currentTimestamp = now;
        }
    }
}
