package ratelimiter;

/**
 * A per-user rate limiter that restricts the number of requests allowed
 * within a sliding one-minute window.
 *
 * <p>Implementations must be <b>thread-safe</b> — multiple threads may call
 * {@link #allowRequest(String)} concurrently for the same or different users.</p>
 *
 * <p>Time is controlled externally via {@link #currentTimestamp()} and
 * {@link #setCurrentTimestamp(long)} so that tests can advance the clock
 * deterministically.</p>
 */
public interface RateLimiter {

    /**
     * Decides whether a request from the given user should be allowed.
     *
     * <p>The decision is based on how many requests the user has already made
     * within the one-minute window ending at {@link #currentTimestamp()}.
     * If the request is allowed, it is <b>counted</b> (i.e. the internal
     * state is updated).</p>
     *
     * @param userId non-null identifier of the requesting user
     * @return {@code true} if the request is within the rate limit,
     *         {@code false} if it should be rejected
     */
    boolean allowRequest(String userId);

    /**
     * Returns the current logical timestamp in milliseconds.
     *
     * @return current timestamp in epoch-millis
     */
    long currentTimestamp();

    /**
     * Advances (or sets) the logical clock to the given timestamp.
     *
     * @param timestampMillis the new current time in epoch-millis
     */
    void setCurrentTimestamp(long timestampMillis);
}

