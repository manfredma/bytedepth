package manfred.bytedepth.adapter.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RateLimitPropertiesTest {

    @Test
    void acceptsEveryValidRedisAndRuleSetting() {
        assertThatCode(() -> validProperties().validate()).doesNotThrowAnyException();
    }

    @Test
    void rejectsEveryInvalidRedisSetting() {
        assertInvalidRedis(properties -> properties.setRedis(null));
        assertInvalidRedis(properties -> properties.getRedis().setHost(null));
        assertInvalidRedis(properties -> properties.getRedis().setHost(" "));
        assertInvalidRedis(properties -> properties.getRedis().setPort(0));
        assertInvalidRedis(properties -> properties.getRedis().setPort(65536));
        assertInvalidRedis(properties -> properties.getRedis().setDatabase(-1));
        assertInvalidRedis(properties -> properties.getRedis().setTimeout(null));
        assertInvalidRedis(properties -> properties.getRedis().setTimeout(Duration.ZERO));
        assertInvalidRedis(properties -> properties.getRedis().setTimeout(Duration.ofSeconds(-1)));
    }

    @Test
    void rejectsEveryInvalidRuleSetting() {
        assertInvalidRule(properties -> properties.setLoginIp(null), "login-ip");
        assertInvalidRule(properties -> properties.getLoginIp().setCapacity(0), "login-ip");
        assertInvalidRule(properties -> properties.getLoginIp().setPeriod(null), "login-ip");
        assertInvalidRule(properties -> properties.getLoginIp().setPeriod(Duration.ZERO), "login-ip");
        assertInvalidRule(properties -> properties.getLoginIp().setPeriod(Duration.ofSeconds(-1)), "login-ip");
        assertInvalidRule(properties -> properties.setLoginUsername(null), "login-username");
        assertInvalidRule(properties -> properties.setRegisterIp(null), "register-ip");
        assertInvalidRule(properties -> properties.setCommentRatingIp(null), "comment-rating-ip");
        assertInvalidRule(properties -> properties.setUploadIp(null), "upload-ip");
    }

    private static void assertInvalidRedis(java.util.function.Consumer<RateLimitProperties> change) {
        RateLimitProperties properties = validProperties();
        change.accept(properties);
        assertThatIllegalStateException().isThrownBy(properties::validate)
                .withMessage("限流 Redis 配置无效");
    }

    private static void assertInvalidRule(java.util.function.Consumer<RateLimitProperties> change, String ruleName) {
        RateLimitProperties properties = validProperties();
        change.accept(properties);
        assertThatIllegalStateException().isThrownBy(properties::validate)
                .withMessage("限流规则配置无效: " + ruleName);
    }

    private static RateLimitProperties validProperties() {
        RateLimitProperties properties = new RateLimitProperties();
        RateLimitProperties.Redis redis = new RateLimitProperties.Redis();
        redis.setHost("redis");
        redis.setPort(6379);
        redis.setDatabase(0);
        redis.setTimeout(Duration.ofSeconds(1));
        properties.setRedis(redis);
        return properties;
    }
}
