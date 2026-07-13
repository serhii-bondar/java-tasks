# Rate Limiter

Implement `RateLimiterImpl` — a **thread-safe**, **per-user** rate limiter using a sliding time window.

> **No AI assistance** — no Copilot, ChatGPT, Claude, or similar tools.

## Rules

- **Window**: sliding, **60 000 ms** — defined as `(now - 60_000, now]`.
  A request at timestamp `T` expires when the clock reaches `T + 60_000`.
- **Limit**: configurable max requests per user per window.
- **Per-user**: each user has an independent quota.
- **Counting**: only accepted requests (`true`) consume quota; rejections
  (`false`) do not.
- **Thread-safe**: concurrent calls for same or different users must be safe.
- **Clock**: time comes from `currentTimestamp()` / `setCurrentTimestamp(long)`,
  not the system clock.
