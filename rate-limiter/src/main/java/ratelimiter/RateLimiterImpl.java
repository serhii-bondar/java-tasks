package ratelimiter;

public class RateLimiterImpl implements RateLimiter {

    public RateLimiterImpl(int limitPerMinute, long initialTimestamp) {
        // TODO
    }

    @Override
    public boolean allowRequest(String userId) {
        // TODO
        return true;
    }

    @Override
    public long currentTimestamp() {
        // TODO
        return 0;
    }

    @Override
    public void setCurrentTimestamp(long timestampMillis) {
        // TODO
    }
}