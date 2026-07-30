package manfred.bytedepth.adapter.web.ratelimit;

public interface RateLimitService {

    RateLimitDecision tryConsume(String ruleName, RateLimitProperties.Rule rule, String identity);
}
