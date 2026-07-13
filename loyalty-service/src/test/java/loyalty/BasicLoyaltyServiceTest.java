package loyalty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;

class BasicLoyaltyServiceTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2025, 6, 15);
    private LoyaltyService service;

    @BeforeEach
    void setUp() {
        service = new LoyaltyServiceImpl((userId, days) -> days * 1.0);
    }

    private LoginEvent login(String userId, LocalDate date) {
        return new LoginEvent(userId, date.atTime(LocalTime.NOON));
    }

    @Test
    @DisplayName("processLogin returns a non-null result")
    void processLogin_returnsResult() {
        LoyaltyResult result = service.processLogin(login("user1", BASE_DATE));
        assertNotNull(result, "processLogin must not return null");
    }

    @Test
    @DisplayName("Result contains non-negative distinct login days")
    void result_hasNonNegativeDays() {
        LoyaltyResult result = service.processLogin(login("user1", BASE_DATE));
        assertTrue(result.getDistinctLoginDays() >= 0, "distinctLoginDays must be >= 0");
    }

    @Test
    @DisplayName("Result contains non-negative bonus")
    void result_hasNonNegativeBonus() {
        LoyaltyResult result = service.processLogin(login("user1", BASE_DATE));
        assertTrue(result.getBonus() >= 0.0, "bonus must be >= 0");
    }

    @Test
    @DisplayName("isLoyal does not throw for unknown user")
    void isLoyal_unknownUser_doesNotThrow() {
        assertDoesNotThrow(() -> service.isLoyal("unknown"));
    }

    @Test
    @DisplayName("Processing two events for the same user does not throw")
    void twoEvents_sameUser_noException() {
        service.processLogin(login("user1", BASE_DATE));
        LoyaltyResult r = service.processLogin(login("user1", BASE_DATE.plusDays(1)));
        assertNotNull(r);
    }

    @Test
    @DisplayName("Different users are independent")
    void differentUsers_independent() {
        service.processLogin(login("a", BASE_DATE));
        service.processLogin(login("b", BASE_DATE));
        // just verify no cross-contamination crash
        assertDoesNotThrow(() -> service.isLoyal("a"));
        assertDoesNotThrow(() -> service.isLoyal("b"));
    }
}

