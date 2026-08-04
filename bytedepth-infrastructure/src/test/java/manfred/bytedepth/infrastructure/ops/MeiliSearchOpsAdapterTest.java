package manfred.bytedepth.infrastructure.ops;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class MeiliSearchOpsAdapterTest {

    @Test
    void inspectChecksHealthAndStatsWithConfiguredAuthorization() throws IOException {
        List<String> paths = new CopyOnWriteArrayList<>();
        List<String> authorizations = new CopyOnWriteArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            paths.add(exchange.getRequestURI().getPath());
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            MeiliSearchOpsAdapter adapter = new MeiliSearchOpsAdapter(
                    "http://localhost:" + server.getAddress().getPort(), "test-api-key");

            assertEquals(new manfred.bytedepth.app.ops.OpsMeiliSearchStatusDTO(true, true), adapter.inspect());
            assertEquals(List.of("/health", "/stats"), paths);
            assertEquals(List.of("Bearer test-api-key", "Bearer test-api-key"), authorizations);
        } finally {
            server.stop(0);
        }
    }

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
