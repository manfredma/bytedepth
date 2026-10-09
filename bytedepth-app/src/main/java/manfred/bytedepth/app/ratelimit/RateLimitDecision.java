package manfred.bytedepth.app.ratelimit;

public record RateLimitDecision(boolean allowed, long nanosToWaitForRefill) {

    public static RateLimitDecision permit() {
        return new RateLimitDecision(true, 0);
    }

    public static RateLimitDecision rejected(long nanosToWaitForRefill) {
        return new RateLimitDecision(false, Math.max(0, nanosToWaitForRefill));
    }
}
