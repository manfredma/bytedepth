package manfred.bytedepth.infrastructure.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Prefixes application-owned Redis keys while preserving legacy keys when unset. */
@Component
public class RedisKeyNamespace {
    private final String namespace;

    public RedisKeyNamespace(@Value("${bytedepth.redis.key-namespace:}") String namespace) {
        this.namespace = namespace;
    }

    public String prefix(String family) {
        return namespace + family;
    }

    public String key(String family, String suffix) {
        return prefix(family) + suffix;
    }
}
