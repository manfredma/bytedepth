package manfred.bytedepth.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RedisRateLimitAdapterITTest {

    @Test
    void stagingPropertiesUseComposeDnsAndDefaultRedisPort() {
        RateLimitRedisProperties properties = RedisRateLimitAdapterIT.stagingProperties("redis", "secret");

        assertThat(properties.getHost()).isEqualTo("redis");
        assertThat(properties.getPort()).isEqualTo(6379);
        assertThat(properties.getPassword()).isEqualTo("secret");
    }
}
