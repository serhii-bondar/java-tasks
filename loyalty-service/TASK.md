# Loyalty System

Your task is to:
- implement `LoyaltyServiceImpl`;
- ensure it is covered by a **comprehensive** test suite (feel free to add additional tests).

> **Important**: Any AI-assisted tools, agents and IDE plugins (Claude Code, Copilot, AugmentCode etc) are allowed and encouraged!

## Specification

The Loyalty Service tracks user login activity and updates a user’s loyalty status based on recent logins.

- A user is considered **loyal** if they have logged in on **5 or more distinct calendar days** 
within a **rolling 14-day window**  
  (inclusive of both ends, measured backwards from the current login event date).
- Multiple logins on the same calendar day count as **one** login day.
- Loyalty status is **re-evaluated on every `processLogin` call** — a user may gain or lose loyal status as older logins fall outside the rolling window.
- When a user is loyal, a bonus is calculated using the injected `LoyaltyBonusProvider`.