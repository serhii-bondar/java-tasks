package ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    /** 5 requests per minute per user. */
    private static final int MAX_REQUESTS_PER_MINUTE = 5;

    /** Logical start time (epoch-millis 0). */
    private static final long T0 = 0;

    private RateLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new RateLimiterImpl(MAX_REQUESTS_PER_MINUTE, T0);
    }

    // ---------------------------------------------------------------
    //  Helpers
    // ---------------------------------------------------------------

    private static void runInParallel(Runnable task, int times) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(times);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < times; i++) {
                futures.add(pool.submit(task));
            }
            for (Future<?> f : futures) {
                f.get(); // propagates exceptions
            }
        } finally {
            pool.shutdownNow();
        }
    }

    // ===============================================================
    //  1. BASIC RATE LIMITING
    // ===============================================================

    @Nested
    @DisplayName("Basic rate limiting")
    class BasicRateLimiting {

        @Test
        @DisplayName("Requests within the limit are allowed")
        void requestsWithinLimit_allowed() {
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Request " + (i + 1) + " should be allowed");
            }
        }

        @Test
        @DisplayName("Request exceeding the limit is rejected")
        void requestExceedingLimit_rejected() {
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                limiter.allowRequest("user1");
            }
            assertFalse(limiter.allowRequest("user1"),
                    "6th request should be rejected");
        }

        @Test
        @DisplayName("Single request is always allowed")
        void singleRequest_allowed() {
            assertTrue(limiter.allowRequest("user1"));
        }
    }

    // ===============================================================
    //  2. SLIDING WINDOW EXPIRY
    // ===============================================================

    @Nested
    @DisplayName("Sliding window expiry")
    class SlidingWindow {

        @Test
        @DisplayName("After one minute passes, the window resets and new requests are allowed")
        void windowResets_afterOneMinute() {
            // Exhaust the limit at T0
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                limiter.allowRequest("user1");
            }
            assertFalse(limiter.allowRequest("user1"));

            // Advance past the 1-minute window
            limiter.setCurrentTimestamp(T0 + TimeUnit.MINUTES.toMillis(1) + 1);
            assertTrue(limiter.allowRequest("user1"),
                    "Request should be allowed after window expires");
        }

        @Test
        @DisplayName("Requests made late in the window still count until the window slides past them")
        void lateRequests_countUntilExpiry() {
            // 3 requests at T0 (00:00)
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.allowRequest("user1"));
            }

            // 2 more requests at 00:58 — total 5 within [00:00 .. 00:58]
            limiter.setCurrentTimestamp(T0 + TimeUnit.SECONDS.toMillis(58));
            for (int i = 0; i < 2; i++) {
                assertTrue(limiter.allowRequest("user1"));
            }

            // Now at 00:58, limit is reached
            assertFalse(limiter.allowRequest("user1"),
                    "Limit should be reached (5 requests in window)");
        }

        @Test
        @DisplayName("Old requests expire and free up capacity")
        void oldRequests_expire() {
            // 3 requests at 00:00
            for (int i = 0; i < 3; i++) {
                limiter.allowRequest("user1");
            }

            // 2 requests at 00:58
            limiter.setCurrentTimestamp(T0 + TimeUnit.SECONDS.toMillis(58));
            limiter.allowRequest("user1");
            limiter.allowRequest("user1");

            // At 01:59 the 3 requests from 00:00 have expired
            // (window is [00:59 .. 01:59]), only the 2 from 00:58 remain
            limiter.setCurrentTimestamp(T0 + TimeUnit.SECONDS.toMillis(58)
                    + TimeUnit.SECONDS.toMillis(61));
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Request " + (i + 1) + " should be allowed after old ones expired");
            }
        }
    }

    // ===============================================================
    //  3. MULTI-USER ISOLATION
    // ===============================================================

    @Nested
    @DisplayName("Multi-user isolation")
    class MultiUser {

        @Test
        @DisplayName("One user's requests do not affect another user's quota")
        void usersAreIsolated() {
            // Exhaust user1's limit
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                limiter.allowRequest("user1");
            }
            assertFalse(limiter.allowRequest("user1"));

            // user2 should still have full quota
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user2"),
                        "user2 request " + (i + 1) + " should be allowed");
            }
        }

        @Test
        @DisplayName("Multiple users can each use their full quota")
        void multipleUsers_fullQuota() {
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("userA"));
                assertTrue(limiter.allowRequest("userB"));
                assertTrue(limiter.allowRequest("userC"));
            }
        }
    }

    // ===============================================================
    //  4. CONCURRENCY
    // ===============================================================

    @Nested
    @DisplayName("Concurrent access")
    class Concurrency {

        @Test
        @DisplayName("Parallel requests within limit are all allowed")
        void parallelRequests_withinLimit_allowed() throws Exception {
            runInParallel(() -> assertTrue(limiter.allowRequest("user1")), 3);
        }

        @Test
        @DisplayName("Parallel requests exceeding limit are correctly rejected")
        void parallelRequests_exceedingLimit_rejected() throws Exception {
            // Use up 3 slots
            runInParallel(() -> assertTrue(limiter.allowRequest("user1")), 3);

            // 2 more should be allowed
            limiter.setCurrentTimestamp(T0 + TimeUnit.SECONDS.toMillis(58));
            runInParallel(() -> assertTrue(limiter.allowRequest("user1")), 2);

            // All further requests should be rejected
            runInParallel(() -> assertFalse(limiter.allowRequest("user1")), 5);
        }

        @Test
        @DisplayName("Parallel requests for different users do not interfere")
        void parallelRequests_differentUsers() throws Exception {
            // Exhaust user1
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                limiter.allowRequest("user1");
            }

            // user2 requests in parallel should all succeed
            runInParallel(() -> assertTrue(limiter.allowRequest("user2")), 4);
        }
    }

    // ===============================================================
    //  5. ORIGINAL SCENARIO (end-to-end)
    // ===============================================================

    @Nested
    @DisplayName("End-to-end scenario from original specification")
    class EndToEnd {

        @Test
        @DisplayName("Full scenario: allow, exhaust, expire, allow, reject")
        void fullScenario() throws Exception {
            String user1 = "user42";
            String user2 = "user43";

            // 3 requests at 00:00 — allowed
            runInParallel(() -> assertTrue(limiter.allowRequest(user1)), 3);

            // Advance to 00:58
            limiter.setCurrentTimestamp(T0 + TimeUnit.SECONDS.toMillis(58));

            // 2 more for user1 — allowed (total 5 in window)
            runInParallel(() -> assertTrue(limiter.allowRequest(user1)), 2);

            // 4 for user2 — allowed (separate quota)
            runInParallel(() -> assertTrue(limiter.allowRequest(user2)), 4);

            // user1 is now at limit — reject
            runInParallel(() -> assertFalse(limiter.allowRequest(user1)), 5);

            // Advance to 01:59 — the 3 requests from 00:00 have expired
            limiter.setCurrentTimestamp(T0 + TimeUnit.SECONDS.toMillis(58)
                    + TimeUnit.SECONDS.toMillis(61));

            // user1 gets a fresh window — 5 allowed
            runInParallel(() -> assertTrue(limiter.allowRequest(user1)), 5);

            // user1 is at limit again — reject
            assertFalse(limiter.allowRequest(user1));
        }
    }

    // ===============================================================
    //  6. EDGE CASES
    // ===============================================================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("Exactly at the 1-minute boundary — old request just expired")
        void exactBoundary_expired() {
            limiter.allowRequest("user1"); // at T0
            // Advance exactly 1 minute
            limiter.setCurrentTimestamp(T0 + TimeUnit.MINUTES.toMillis(1));
            // The request at T0 is now exactly 60_000ms ago — outside the window
            // Full quota should be available
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Request " + (i + 1) + " should be allowed at exact boundary");
            }
        }

        @Test
        @DisplayName("Request just before the boundary — still in window")
        void justBeforeBoundary_stillInWindow() {
            // Use all 5 slots at T0
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                limiter.allowRequest("user1");
            }
            // 1ms before the minute mark — requests are still in window
            limiter.setCurrentTimestamp(T0 + TimeUnit.MINUTES.toMillis(1) - 1);
            assertFalse(limiter.allowRequest("user1"),
                    "Requests should still be in window 1ms before expiry");
        }

        @Test
        @DisplayName("Unknown user's first request is always allowed")
        void unknownUser_allowed() {
            assertTrue(limiter.allowRequest("brand-new-user"));
        }

        @Test
        @DisplayName("Limit of 1 request per minute")
        void limitOfOne() {
            RateLimiter strict = new RateLimiterImpl(1, T0);
            assertTrue(strict.allowRequest("user1"));
            assertFalse(strict.allowRequest("user1"));

            strict.setCurrentTimestamp(T0 + TimeUnit.MINUTES.toMillis(1));
            assertTrue(strict.allowRequest("user1"));
        }
    }

    // ===============================================================
    //  7. REJECTED REQUESTS DO NOT CONSUME QUOTA
    // ===============================================================

    @Nested
    @DisplayName("Rejected requests do not consume quota")
    class RejectedRequestsQuota {

        @Test
        @DisplayName("Rejected requests do not reduce remaining capacity")
        void rejectedRequests_doNotConsumeQuota() {
            // Fill up the quota
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"));
            }
            // Send 10 rejected requests
            for (int i = 0; i < 10; i++) {
                assertFalse(limiter.allowRequest("user1"));
            }
            // Advance past window — all 5 original requests expire
            limiter.setCurrentTimestamp(T0 + TimeUnit.MINUTES.toMillis(1) + 1);
            // Full quota should be available (rejected ones must not have consumed slots)
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Request " + (i + 1) + " should be allowed — rejected requests must not consume quota");
            }
            assertFalse(limiter.allowRequest("user1"), "Limit reached again");
        }

        @Test
        @DisplayName("After partial expiry, rejected requests don't affect remaining capacity")
        void partialExpiry_rejectedRequestsDontAffect() {
            // 3 requests at T0
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.allowRequest("user1"));
            }
            // 2 requests at T0 + 30s
            limiter.setCurrentTimestamp(T0 + 30_000);
            for (int i = 0; i < 2; i++) {
                assertTrue(limiter.allowRequest("user1"));
            }
            // Limit reached — send rejected requests
            for (int i = 0; i < 5; i++) {
                assertFalse(limiter.allowRequest("user1"));
            }
            // Advance to T0 + 60_001 — the 3 from T0 expire, 2 from T0+30s remain
            limiter.setCurrentTimestamp(T0 + 60_001);
            // Should have 3 slots available (5 - 2 remaining)
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Request " + (i + 1) + " should be allowed after partial expiry");
            }
            assertFalse(limiter.allowRequest("user1"),
                    "Limit should be reached (2 old + 3 new = 5)");
        }
    }

    // ===============================================================
    //  8. HALF-OPEN INTERVAL PRECISION
    // ===============================================================

    @Nested
    @DisplayName("Half-open interval (now - 60_000, now]")
    class HalfOpenInterval {

        @Test
        @DisplayName("Request at exactly now - 60_000 is OUTSIDE the window")
        void requestAtExactBoundary_isOutside() {
            // Request at T0
            assertTrue(limiter.allowRequest("user1"));
            // Advance to T0 + 60_000 — request at T0 is exactly 60_000ms ago
            // Window is (T0, T0+60_000], so T0 is OUTSIDE
            limiter.setCurrentTimestamp(T0 + 60_000);
            // Full quota available
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Request " + (i + 1) + " — T0 request should be outside half-open window");
            }
        }

        @Test
        @DisplayName("Request at now - 59_999 is INSIDE the window")
        void requestAt59999msAgo_isInside() {
            // Fill quota at T0
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"));
            }
            // Advance to T0 + 59_999 — requests at T0 are 59_999ms ago, still inside
            limiter.setCurrentTimestamp(T0 + 59_999);
            assertFalse(limiter.allowRequest("user1"),
                    "Requests at T0 should still be inside window at T0+59_999");
        }

        @Test
        @DisplayName("Requests at different timestamps expire independently at exact boundary")
        void requestsExpireIndependently_atExactBoundary() {
            // 2 requests at T0
            assertTrue(limiter.allowRequest("user1"));
            assertTrue(limiter.allowRequest("user1"));
            // 3 requests at T0 + 100
            limiter.setCurrentTimestamp(T0 + 100);
            assertTrue(limiter.allowRequest("user1"));
            assertTrue(limiter.allowRequest("user1"));
            assertTrue(limiter.allowRequest("user1"));
            // Limit reached
            assertFalse(limiter.allowRequest("user1"));

            // At T0 + 60_000: the 2 from T0 expire (exactly 60_000ms ago = outside)
            // The 3 from T0+100 are 59_900ms ago = still inside
            limiter.setCurrentTimestamp(T0 + 60_000);
            // 2 slots freed
            assertTrue(limiter.allowRequest("user1"), "1st slot freed");
            assertTrue(limiter.allowRequest("user1"), "2nd slot freed");
            assertFalse(limiter.allowRequest("user1"),
                    "Only 2 slots freed — 3 from T0+100 still in window");

            // At T0 + 60_100: the 3 from T0+100 also expire
            // But the 2 requests added at T0+60_000 are still in the window → 3 slots free
            limiter.setCurrentTimestamp(T0 + 60_100);
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Slot " + (i + 1) + " of 3 freed (2 from T0+60_000 still in window)");
            }
            assertFalse(limiter.allowRequest("user1"),
                    "Limit reached: 2 from T0+60_000 + 3 new = 5");
        }
    }

    // ===============================================================
    //  9. CLOCK / TIMESTAMP CORRECTNESS
    // ===============================================================

    @Nested
    @DisplayName("Clock and timestamp correctness")
    class ClockCorrectness {

        @Test
        @DisplayName("currentTimestamp returns initial value after construction")
        void currentTimestamp_returnsInitialValue() {
            RateLimiter fresh = new RateLimiterImpl(MAX_REQUESTS_PER_MINUTE, 42_000L);
            assertEquals(42_000L, fresh.currentTimestamp());
        }

        @Test
        @DisplayName("currentTimestamp returns updated value after setCurrentTimestamp")
        void currentTimestamp_returnsUpdatedValue() {
            limiter.setCurrentTimestamp(99_999L);
            assertEquals(99_999L, limiter.currentTimestamp());
        }

        @Test
        @DisplayName("Large realistic timestamp values work correctly")
        void largeTimestampValues() {
            long realEpoch = 1_700_000_000_000L; // ~Nov 2023
            RateLimiter realTime = new RateLimiterImpl(MAX_REQUESTS_PER_MINUTE, realEpoch);

            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(realTime.allowRequest("user1"));
            }
            assertFalse(realTime.allowRequest("user1"));

            // Advance 1 minute
            realTime.setCurrentTimestamp(realEpoch + 60_001);
            assertTrue(realTime.allowRequest("user1"),
                    "Should work with large epoch-millis values");
        }
    }

    // ===============================================================
    //  10. PARTIAL EXPIRY AND CAPACITY
    // ===============================================================

    @Nested
    @DisplayName("Partial expiry and exact capacity")
    class PartialExpiry {

        @Test
        @DisplayName("3 at T0, 2 at T+30s — at T+60_001 only 3 expire, 2 remain")
        void partialExpiry_exactCapacity() {
            // 3 requests at T0
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.allowRequest("user1"));
            }
            // 2 requests at T0 + 30_000
            limiter.setCurrentTimestamp(T0 + 30_000);
            assertTrue(limiter.allowRequest("user1"));
            assertTrue(limiter.allowRequest("user1"));

            // At T0 + 60_001: 3 from T0 expired, 2 from T0+30_000 remain
            limiter.setCurrentTimestamp(T0 + 60_001);
            // Remaining capacity = 5 - 2 = 3
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Slot " + (i + 1) + " of 3 freed should be available");
            }
            assertFalse(limiter.allowRequest("user1"),
                    "Limit reached: 2 old + 3 new = 5");
        }

        @Test
        @DisplayName("Requests at 5 different timestamps — each expires independently")
        void fiveTimestamps_independentExpiry() {
            // 1 request each at T0, T0+10s, T0+20s, T0+30s, T0+40s
            for (int i = 0; i < 5; i++) {
                limiter.setCurrentTimestamp(T0 + i * 10_000L);
                assertTrue(limiter.allowRequest("user1"));
            }
            // Limit reached at T0+40s
            assertFalse(limiter.allowRequest("user1"));

            // At T0+60_000: request from T0 expires (exactly 60s ago = outside)
            limiter.setCurrentTimestamp(T0 + 60_000);
            assertTrue(limiter.allowRequest("user1"), "1 slot freed (T0 expired)");
            assertFalse(limiter.allowRequest("user1"), "Only 1 slot was freed");

            // At T0+70_000: request from T0+10s also expires
            limiter.setCurrentTimestamp(T0 + 70_000);
            assertTrue(limiter.allowRequest("user1"), "1 more slot freed (T0+10s expired)");
            assertFalse(limiter.allowRequest("user1"));

            // At T0+100_000: all original requests expired (T0+40s is 60s ago)
            // But requests added at T0+60_000 and T0+70_000 are still in window → 3 slots free
            limiter.setCurrentTimestamp(T0 + 100_000);
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Slot " + (i + 1) + " of 3 freed (2 intermediate requests still in window)");
            }
            assertFalse(limiter.allowRequest("user1"),
                    "Limit reached: 2 intermediate + 3 new = 5");
        }

        @Test
        @DisplayName("Full expiry gives complete fresh quota")
        void fullExpiry_freshQuota() {
            // Fill and exhaust
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                limiter.allowRequest("user1");
            }
            assertFalse(limiter.allowRequest("user1"));

            // Jump far into the future
            limiter.setCurrentTimestamp(T0 + 1_000_000);
            // Full quota
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Request " + (i + 1) + " should be allowed with fresh quota");
            }
            assertFalse(limiter.allowRequest("user1"));
        }
    }

    // ===============================================================
    //  11. LIMIT OF ZERO AND HIGH LIMITS
    // ===============================================================

    @Nested
    @DisplayName("Extreme limits")
    class ExtremeLimits {

        @Test
        @DisplayName("Limit of 0 — every request is rejected")
        void limitOfZero_allRejected() {
            RateLimiter zeroLimit = new RateLimiterImpl(0, T0);
            assertFalse(zeroLimit.allowRequest("user1"), "Limit 0: first request rejected");
            assertFalse(zeroLimit.allowRequest("user1"), "Limit 0: second request rejected");
            assertFalse(zeroLimit.allowRequest("user2"), "Limit 0: different user also rejected");

            // Even after time passes
            zeroLimit.setCurrentTimestamp(T0 + 120_000);
            assertFalse(zeroLimit.allowRequest("user1"), "Limit 0: still rejected after time");
        }

        @Test
        @DisplayName("High limit (100) — all requests within limit are allowed")
        void highLimit_allAllowed() {
            RateLimiter highLimit = new RateLimiterImpl(100, T0);
            for (int i = 0; i < 100; i++) {
                assertTrue(highLimit.allowRequest("user1"),
                        "Request " + (i + 1) + " of 100 should be allowed");
            }
            assertFalse(highLimit.allowRequest("user1"), "101st request should be rejected");
        }
    }

    // ===============================================================
    //  12. CONCURRENCY STRESS
    // ===============================================================

    @Nested
    @DisplayName("Concurrency stress tests")
    class ConcurrencyStress {

        @Test
        @DisplayName("Exactly LIMIT requests allowed under heavy contention")
        void exactLimitUnderContention() throws Exception {
            int threads = 20;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            try {
                List<Future<Boolean>> futures = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futures.add(pool.submit(() -> limiter.allowRequest("user1")));
                }
                int allowed = 0;
                int rejected = 0;
                for (Future<Boolean> f : futures) {
                    if (f.get()) allowed++;
                    else rejected++;
                }
                assertEquals(MAX_REQUESTS_PER_MINUTE, allowed,
                        "Exactly " + MAX_REQUESTS_PER_MINUTE + " requests should be allowed under contention");
                assertEquals(threads - MAX_REQUESTS_PER_MINUTE, rejected,
                        "Exactly " + (threads - MAX_REQUESTS_PER_MINUTE) + " should be rejected");
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        @DisplayName("Multiple users under contention — each gets exactly LIMIT")
        void multipleUsersUnderContention() throws Exception {
            int threads = 10;
            ExecutorService pool = Executors.newFixedThreadPool(threads * 3);
            try {
                List<Future<Boolean>> futuresA = new ArrayList<>();
                List<Future<Boolean>> futuresB = new ArrayList<>();
                List<Future<Boolean>> futuresC = new ArrayList<>();
                for (int i = 0; i < threads; i++) {
                    futuresA.add(pool.submit(() -> limiter.allowRequest("userA")));
                    futuresB.add(pool.submit(() -> limiter.allowRequest("userB")));
                    futuresC.add(pool.submit(() -> limiter.allowRequest("userC")));
                }
                int allowedA = 0, allowedB = 0, allowedC = 0;
                for (Future<Boolean> f : futuresA) if (f.get()) allowedA++;
                for (Future<Boolean> f : futuresB) if (f.get()) allowedB++;
                for (Future<Boolean> f : futuresC) if (f.get()) allowedC++;

                assertEquals(MAX_REQUESTS_PER_MINUTE, allowedA, "User A should get exactly " + MAX_REQUESTS_PER_MINUTE);
                assertEquals(MAX_REQUESTS_PER_MINUTE, allowedB, "User B should get exactly " + MAX_REQUESTS_PER_MINUTE);
                assertEquals(MAX_REQUESTS_PER_MINUTE, allowedC, "User C should get exactly " + MAX_REQUESTS_PER_MINUTE);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    // ===============================================================
    //  13. INTERLEAVED TIME ADVANCES
    // ===============================================================

    @Nested
    @DisplayName("Interleaved time advances and requests")
    class InterleavedTimeAdvances {

        @Test
        @DisplayName("Multiple small time advances with interleaved requests")
        void multipleSmallAdvances() {
            // Request at T0
            assertTrue(limiter.allowRequest("user1"));

            // Advance 20s, request
            limiter.setCurrentTimestamp(T0 + 20_000);
            assertTrue(limiter.allowRequest("user1"));

            // Advance 20s more (T0+40s), request
            limiter.setCurrentTimestamp(T0 + 40_000);
            assertTrue(limiter.allowRequest("user1"));

            // Advance 10s more (T0+50s), 2 requests
            limiter.setCurrentTimestamp(T0 + 50_000);
            assertTrue(limiter.allowRequest("user1"));
            assertTrue(limiter.allowRequest("user1"));

            // Limit reached (5 requests, all within window)
            assertFalse(limiter.allowRequest("user1"));

            // Advance to T0+60_000: request from T0 expires
            limiter.setCurrentTimestamp(T0 + 60_000);
            assertTrue(limiter.allowRequest("user1"), "T0 request expired, 1 slot free");
            assertFalse(limiter.allowRequest("user1"));

            // Advance to T0+80_000: request from T0+20s expires
            limiter.setCurrentTimestamp(T0 + 80_000);
            assertTrue(limiter.allowRequest("user1"), "T0+20s request expired");
            assertFalse(limiter.allowRequest("user1"));
        }

        @Test
        @DisplayName("Interleaved requests from multiple users across time advances")
        void interleavedMultiUser_acrossTimeAdvances() {
            // T0: user1 x3, user2 x2
            for (int i = 0; i < 3; i++) assertTrue(limiter.allowRequest("user1"));
            for (int i = 0; i < 2; i++) assertTrue(limiter.allowRequest("user2"));

            // T0+30s: user1 x2 (limit), user2 x3 (limit)
            limiter.setCurrentTimestamp(T0 + 30_000);
            assertTrue(limiter.allowRequest("user1"));
            assertTrue(limiter.allowRequest("user1"));
            assertFalse(limiter.allowRequest("user1"), "user1 at limit");

            assertTrue(limiter.allowRequest("user2"));
            assertTrue(limiter.allowRequest("user2"));
            assertTrue(limiter.allowRequest("user2"));
            assertFalse(limiter.allowRequest("user2"), "user2 at limit");

            // T0+60_001: user1's 3 from T0 expire, user2's 2 from T0 expire
            limiter.setCurrentTimestamp(T0 + 60_001);
            // user1: 2 from T0+30s remain → 3 slots free
            for (int i = 0; i < 3; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "user1 slot " + (i + 1) + " of 3 freed");
            }
            assertFalse(limiter.allowRequest("user1"));

            // user2: 3 from T0+30s remain → 2 slots free
            for (int i = 0; i < 2; i++) {
                assertTrue(limiter.allowRequest("user2"),
                        "user2 slot " + (i + 1) + " of 2 freed");
            }
            assertFalse(limiter.allowRequest("user2"));
        }

        @Test
        @DisplayName("Multiple windows worth of time passing — clean slate")
        void multipleWindowsPassing_cleanSlate() {
            // Fill quota
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                limiter.allowRequest("user1");
            }
            // Jump 5 minutes ahead
            limiter.setCurrentTimestamp(T0 + 5 * 60_000);
            // Complete fresh quota
            for (int i = 0; i < MAX_REQUESTS_PER_MINUTE; i++) {
                assertTrue(limiter.allowRequest("user1"),
                        "Request " + (i + 1) + " after 5-minute gap");
            }
            assertFalse(limiter.allowRequest("user1"));
        }
    }
}