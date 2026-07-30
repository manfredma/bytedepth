package manfred.bytedepth.infrastructure.ops;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class MeiliSearchOpsAdapterTest {

    @Test
    void inspect_timesOutWhenMeiliSearchDoesNotRespond() throws IOException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Socket> acceptedConnection = null;

        try (ServerSocket blackhole = new ServerSocket(0)) {
            acceptedConnection = executor.submit(blackhole::accept);
            MeiliSearchOpsAdapter adapter = new MeiliSearchOpsAdapter(
                    "http://localhost:" + blackhole.getLocalPort(), "test-api-key");

            assertTimeoutPreemptively(
                    MeiliSearchOpsAdapter.READ_TIMEOUT.plusSeconds(2),
                    () -> assertThrows(ResourceAccessException.class, adapter::inspect));
        } finally {
            if (acceptedConnection != null && acceptedConnection.isDone()) {
                try {
                    acceptedConnection.get().close();
                } catch (Exception ignored) {
                    // The socket is only test infrastructure and may already be closed.
                }
            }
            executor.shutdownNow();
        }
    }
}
