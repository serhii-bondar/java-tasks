package loyalty;

import java.util.Objects;

/**
 * The outcome of processing a single {@link LoginEvent} through the loyalty
 * engine.
 *
 * <p>Returned by {@link LoyaltyService#processLogin(LoginEvent)}.</p>
 */
public final class LoyaltyResult {

    private final boolean loyal;
    private final int distinctLoginDays;
    private final double bonus;

    /**
     * @param loyal             whether the user meets the loyalty threshold
     * @param distinctLoginDays distinct calendar days counted inside the
     *                          rolling 14-day window
     * @param bonus             the bonus awarded (0.0 when not loyal)
     */
    public LoyaltyResult(boolean loyal, int distinctLoginDays, double bonus) {
        this.loyal = loyal;
        this.distinctLoginDays = distinctLoginDays;
        this.bonus = bonus;
    }

    public boolean isLoyal() {
        return loyal;
    }

    public int getDistinctLoginDays() {
        return distinctLoginDays;
    }

    public double getBonus() {
        return bonus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LoyaltyResult that)) return false;
        return loyal == that.loyal
                && distinctLoginDays == that.distinctLoginDays
                && Double.compare(bonus, that.bonus) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(loyal, distinctLoginDays, bonus);
    }

    @Override
    public String toString() {
        return "LoyaltyResult{loyal=%s, distinctLoginDays=%d, bonus=%.2f}"
                .formatted(loyal, distinctLoginDays, bonus);
    }
}
