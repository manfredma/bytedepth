package manfred.bytedepth.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class RedisRateLimitAdapterTest {

    @Test
    void constructsRedisUrisWithoutAndWithAPassword() {
        RedisClient redisClient = mock(RedisClient.class);
        ArgumentCaptor<RedisURI> redisUris = ArgumentCaptor.forClass(RedisURI.class);
        try (MockedStatic<RedisClient> clients = mockStatic(RedisClient.class)) {
            clients.when(() -> RedisClient.create(any(RedisURI.class))).thenReturn(redisClient);

            RateLimitRedisProperties blank = new RateLimitRedisProperties();
            blank.setPassword(null);
            new RedisRateLimitAdapter(blank).close();

            RateLimitRedisProperties configured = new RateLimitRedisProperties();
            configured.setPassword("secret");
            new RedisRateLimitAdapter(configured).close();

            clients.verify(() -> RedisClient.create(redisUris.capture()), org.mockito.Mockito.times(2));
        }

        assertThat(redisUris.getAllValues()).hasSize(2);
        assertThat(redisUris.getAllValues().get(0).getCredentialsProvider().resolveCredentials().block().hasPassword())
                .isFalse();
        assertThat(redisUris.getAllValues().get(1).getCredentialsProvider().resolveCredentials().block().getPassword())
                .containsExactly('s', 'e', 'c', 'r', 'e', 't');
    }

    @Test
    void managerReturnsTheInstancePublishedByAConcurrentInitializer() throws Exception {
        RedisRateLimitAdapter adapter = newAdapterWithoutNetwork();
        ProxyManager<byte[]> concurrentlyInitialized = proxyManagerMock();
        AtomicReference<Thread> caller = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "rate-limit-manager-caller");
            caller.set(thread);
            return thread;
        });
        AtomicReference<Future<ProxyManager<byte[]>>> resultHolder = new AtomicReference<>();
        try {
            synchronized (adapter) {
                Future<ProxyManager<byte[]>> result = executor.submit(() -> managerFor(adapter));
                awaitBlocked(caller);
                proxyManagerField().set(adapter, concurrentlyInitialized);
                resultHolder.set(result);
            }
            assertSame(concurrentlyInitialized, resultHolder.get().get());
        } finally {
            executor.shutdownNow();
            adapter.close();
        }
    }

    @Test
    void sha256ReportsANonRecoverableJvmDigestFailure() throws Exception {
        RedisRateLimitAdapter adapter = newAdapterWithoutNetwork();
        try (MockedStatic<MessageDigest> digest = mockStatic(MessageDigest.class)) {
            digest.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("unavailable"));

            InvocationTargetException exception = assertThrows(InvocationTargetException.class,
                    () -> sha256(adapter, "visitor"));

            assertEquals("JVM 缺少 SHA-256", exception.getCause().getMessage());
        } finally {
            adapter.close();
        }
    }

    private static RedisRateLimitAdapter newAdapterWithoutNetwork() {
        RedisClient redisClient = mock(RedisClient.class);
        try (MockedStatic<RedisClient> clients = mockStatic(RedisClient.class)) {
            clients.when(() -> RedisClient.create(any(RedisURI.class))).thenReturn(redisClient);
            return new RedisRateLimitAdapter(new RateLimitRedisProperties());
        }
    }

    @SuppressWarnings("unchecked")
    private static ProxyManager<byte[]> managerFor(RedisRateLimitAdapter adapter) throws Exception {
        Method manager = RedisRateLimitAdapter.class.getDeclaredMethod("manager");
        manager.setAccessible(true);
        return (ProxyManager<byte[]>) manager.invoke(adapter);
    }

    @SuppressWarnings("unchecked")
    private static ProxyManager<byte[]> proxyManagerMock() {
        return mock(ProxyManager.class);
    }

    private static String sha256(RedisRateLimitAdapter adapter, String value) throws Exception {
        Method sha256 = RedisRateLimitAdapter.class.getDeclaredMethod("sha256", String.class);
        sha256.setAccessible(true);
        return (String) sha256.invoke(adapter, value);
    }

    private static Field proxyManagerField() throws NoSuchFieldException {
        Field field = RedisRateLimitAdapter.class.getDeclaredField("proxyManager");
        field.setAccessible(true);
        return field;
    }

    private static void awaitBlocked(AtomicReference<Thread> caller) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while ((caller.get() == null || caller.get().getState() != Thread.State.BLOCKED)
                && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(Thread.State.BLOCKED, caller.get().getState());
    }
}
