package loyalty;

/**
 * Core contract for the loyalty evaluation engine.
 *
 * <p>Implementations process login events and determine whether a user
 * qualifies as "loyal" based on their recent login activity.</p>
 */
public interface LoyaltyService {

    /**
     * Records a login event and evaluates the user's loyalty status.
     *
     * @param event the login event to process
     * @return a {@link LoyaltyResult} reflecting the user's status after this event
     */
    LoyaltyResult processLogin(LoginEvent event);

    /**
     * Returns the most recently evaluated loyalty status for the given user.
     *
     * @param userId the user identifier
     * @return {@code true} if the user is currently considered loyal,
     *         {@code false} if not loyal or if no events have been processed
     *         for this user
     */
    boolean isLoyal(String userId);
}
