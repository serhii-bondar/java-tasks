package loyalty;

/**
 * Strategy interface for computing the loyalty bonus awarded to a user.
 *
 * <p>Implementations are injected into the loyalty service, making the bonus
 * logic fully configurable without modifying the core evaluation engine.</p>
 */
@FunctionalInterface
public interface LoyaltyBonusProvider {

    /**
     * Calculates the bonus for a loyal user.
     *
     * @param userId           the identifier of the loyal user
     * @param distinctLoginDays number of distinct calendar days the user logged
     *                          in within the rolling 14-day window
     * @return the bonus amount (must be &ge; 0)
     */
    double calculateBonus(String userId, int distinctLoginDays);
}
