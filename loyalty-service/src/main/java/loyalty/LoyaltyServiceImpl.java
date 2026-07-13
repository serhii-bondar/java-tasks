package loyalty;

public class LoyaltyServiceImpl implements LoyaltyService {

    public LoyaltyServiceImpl(LoyaltyBonusProvider bonusProvider) {
        // TODO: implement
    }

    @Override
    public LoyaltyResult processLogin(LoginEvent event) {
        // TODO: implement
        return null;
    }

    @Override
    public boolean isLoyal(String userId) {
        // TODO: implement
        return false;
    }

    public int getRetainedEventCount(String userId) {
        // TODO: implement
        return 0;
    }
}