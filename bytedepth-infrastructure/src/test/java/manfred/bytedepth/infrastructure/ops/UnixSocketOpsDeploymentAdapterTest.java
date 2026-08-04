package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsDeploymentStatusDTO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class UnixSocketOpsDeploymentAdapterTest {

    @Test
    void blankSocketPathDoesNotAttemptDeployment() {
        UnixSocketOpsDeploymentAdapter adapter = new UnixSocketOpsDeploymentAdapter("");

        OpsDeploymentStatusDTO status = adapter.deployRelease("v1.0.0");

        assertFalse(status.available());
        assertEquals("UNAVAILABLE", status.state());
    }

    @Test
    void invalidReleaseVersionIsRejectedBeforeConnectingToTheSocket() {
        OpsDeploymentStatusDTO status = new UnixSocketOpsDeploymentAdapter("/not-used")
                .deployRelease("main");

        assertFalse(status.available());
        assertEquals("REJECTED", status.state());

        OpsDeploymentStatusDTO leadingZeroStatus = new UnixSocketOpsDeploymentAdapter("/not-used")
                .deployRelease("v01.0.0");

        assertFalse(leadingZeroStatus.available());
        assertEquals("REJECTED", leadingZeroStatus.state());

        OpsDeploymentStatusDTO nullStatus = new UnixSocketOpsDeploymentAdapter("/not-used")
                .deployRelease(null);

        assertFalse(nullStatus.available());
        assertEquals("REJECTED", nullStatus.state());
    }

    @Test
    void statusParsesOnlyValidatedVersionAndCommitFromHostResponse() throws Exception {
        OpsDeploymentStatusDTO valid = requestStatus("state=SUCCESS\nversion=v1.2.3\ncommit=0123456\nupdated_at=2026-08-04T00:00:00Z\n");
        OpsDeploymentStatusDTO invalid = requestStatus("state=SUCCESS\nversion=main\ncommit=not-a-commit\nupdated_at=2026-08-04T00:00:00Z\n");

        assertEquals("v1.2.3", valid.version());
        assertEquals("0123456", valid.commit());
        assertNull(invalid.version());
        assertNull(invalid.commit());
    }

    @Test
    void statusCoversEveryHostStateAndUnavailableResponses() throws Exception {
        assertEquals("尚未请求部署。", requestStatus("state=IDLE\n").message());
        assertEquals("部署请求已接收。", requestStatus("state=QUEUED\n").message());
        assertEquals("正在拉取代码并重建服务。", requestStatus("state=RUNNING\n").message());
        assertEquals("最近一次部署失败，请检查服务器部署日志。", requestStatus("state=FAILED\n").message());
        assertEquals("已有部署任务正在运行。", requestStatus("state=BUSY\n").message());
        assertEquals("部署请求被拒绝。", requestStatus("state=REJECTED\n").message());
        assertFalse(requestStatus("not-a-property\nstate=UNKNOWN\n").available());
        assertFalse(new UnixSocketOpsDeploymentAdapter("/tmp/bytedepth-missing.sock").status().available());
    }

    @Test
    void statusReadsAResponseAtTheMaximumSupportedSize() throws Exception {
        String response = "state=IDLE\n" + "x".repeat(4 * 1024 - "state=IDLE\n".length());

        OpsDeploymentStatusDTO status = requestStatus(response);

        assertEquals("IDLE", status.state());
    }

    @Test
    void messageForUnexpectedStateUsesUnavailableFallback() throws Exception {
        UnixSocketOpsDeploymentAdapter adapter = new UnixSocketOpsDeploymentAdapter("");
        Method messageFor = UnixSocketOpsDeploymentAdapter.class.getDeclaredMethod("messageFor", String.class);
        messageFor.setAccessible(true);

        assertEquals("部署服务暂不可用。", messageFor.invoke(adapter, "UNEXPECTED"));
    }

    @Test
    void validReleaseVersionIsSentAsFixedSocketCommand() throws Exception {
        Path directory = Files.createTempDirectory("bytedepth-ops-");
        Path socket = directory.resolve("deploy.sock");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                var request = executor.submit(() -> respondAndReadRequest(server, "state=QUEUED\nversion=v1.2.3\n"));
                OpsDeploymentStatusDTO status = new UnixSocketOpsDeploymentAdapter(socket.toString())
                        .deployRelease("v1.2.3");

                assertEquals("deploy-tag v1.2.3\n", request.get());
                assertEquals("QUEUED", status.state());
                assertEquals("v1.2.3", status.version());
            } finally {
                executor.shutdownNow();
            }
        } finally {
            Files.deleteIfExists(socket);
            Files.deleteIfExists(directory);
        }
    }

    private OpsDeploymentStatusDTO requestStatus(String response) throws Exception {
        Path directory = Files.createTempDirectory("bytedepth-ops-");
        Path socket = directory.resolve("deploy.sock");
        try (ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            server.bind(UnixDomainSocketAddress.of(socket));
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> respond(server, response));
                return new UnixSocketOpsDeploymentAdapter(socket.toString()).status();
            } finally {
                executor.shutdownNow();
            }
        } finally {
            Files.deleteIfExists(socket);
            Files.deleteIfExists(directory);
        }
    }

    private void respond(ServerSocketChannel server, String response) {
        respondAndReadRequest(server, response);
    }

    private String respondAndReadRequest(ServerSocketChannel server, String response) {
        try (SocketChannel client = server.accept()) {
            ByteBuffer request = ByteBuffer.allocate(64);
            client.read(request);
            client.write(StandardCharsets.UTF_8.encode(response));
            return StandardCharsets.UTF_8.decode((ByteBuffer) request.flip()).toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
