# Task 1 Report: Redis Adapter Test Boundary

## Result

- Moved the sole real Redis bucket-consumption scenario into `RedisRateLimitAdapterIT`.
- The integration test requires `bytedepth.it.redis.host`, `bytedepth.it.redis.port`, and `bytedepth.it.redis.password`; its staging helper defaults the two-argument Compose-DNS form to `redis:6379`.
- The integration test generates a UUID rule and deletes its precise Bucket4j Redis key through a dedicated Lettuce cleanup connection in `finally`.
- Retained password URI construction, reflection-based SHA-256 failure behavior, and proxy-manager concurrency coverage in `RedisRateLimitAdapterTest`; every regular test substitutes the Redis client, so no Redis connection is opened.
- `RateLimitRedisProperties` was deliberately not changed: the isolated helper test passed without a production change, per the binding ruling.

## TDD evidence

1. Added `RedisRateLimitAdapterITTest.stagingPropertiesUseComposeDnsAndDefaultRedisPort` first.
2. Ran `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-infrastructure -Dtest=RedisRateLimitAdapterITTest test`.
3. Observed the expected red compilation failure: `RedisRateLimitAdapterIT` was missing.
4. Added the minimal integration helper and test classification, then saw the helper test pass.

## Offline verification

| Command | Result |
| --- | --- |
| `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-infrastructure -Dtest=RedisRateLimitAdapterITTest test` | PASS — 1 test |
| `JAVA_HOME=$(/usr/libexec/java_home -v 25) mvn -pl bytedepth-infrastructure -Dtest=RedisRateLimitAdapterTest test` | PASS — 3 tests |
| `git diff --check` | PASS — no whitespace errors |

No Redis, Docker, integration, or E2E test was run locally. `RedisRateLimitAdapterIT` must be run by staging with all three system properties supplied and hostname `redis` from the Compose network.

## Concern

The staging runner wiring that supplies the three `bytedepth.it.redis.*` properties is outside Task 1 and was not changed here. Staging must invoke the `*IT` class explicitly (or through its integration-test runner).
