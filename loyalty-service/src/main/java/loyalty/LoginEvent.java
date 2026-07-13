package loyalty;

import java.time.LocalDateTime;
import java.util.Objects;

public final class LoginEvent {

    private final String userId;
    private final LocalDateTime timestamp;

    public LoginEvent(String userId, LocalDateTime timestamp) {
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }

    public String getUserId() {
        return userId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoginEvent that)) return false;
        return userId.equals(that.userId) && timestamp.equals(that.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, timestamp);
    }

    @Override
    public String toString() {
        return "LoginEvent{userId='%s', timestamp=%s}".formatted(userId, timestamp);
    }
}
