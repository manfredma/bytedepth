package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsDeploymentPort;
import manfred.bytedepth.app.ops.OpsDeploymentStatusDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Talks only to the local, fixed-command host deployment socket. */
@Component
public class UnixSocketOpsDeploymentAdapter implements OpsDeploymentPort {

    private static final int MAX_RESPONSE_BYTES = 4 * 1024;
    private static final Set<String> STATES = Set.of("IDLE", "QUEUED", "RUNNING", "SUCCESS", "FAILED", "BUSY", "REJECTED");
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{7,40}");
    private static final Pattern VERSION = Pattern.compile("v(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)");

    private final String socketPath;

    public UnixSocketOpsDeploymentAdapter(@Value("${bytedepth.deploy.socket-path:}") String socketPath) {
        this.socketPath = socketPath;
    }

    @Override
    public OpsDeploymentStatusDTO status() {
        return exchange("status");
    }

    @Override
    public OpsDeploymentStatusDTO deployRelease(String version) {
        if (version == null || !VERSION.matcher(version).matches()) {
            return new OpsDeploymentStatusDTO(false, "REJECTED", "版本号格式无效。", null, null, null);
        }
        return exchange("deploy-tag " + version);
    }

    private OpsDeploymentStatusDTO exchange(String command) {
        if (socketPath.isBlank()) {
            return OpsDeploymentStatusDTO.unavailable();
        }
        try (SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.connect(UnixDomainSocketAddress.of(Path.of(socketPath)));
            channel.write(StandardCharsets.UTF_8.encode(command + "\n"));
            return parse(readResponse(channel));
        } catch (IOException e) {
            return OpsDeploymentStatusDTO.unavailable();
        }
    }

    private String readResponse(SocketChannel channel) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(MAX_RESPONSE_BYTES);
        while (buffer.hasRemaining() && channel.read(buffer) != -1) {
            // The host service closes the socket after one small line-based response.
        }
        return StandardCharsets.UTF_8.decode((ByteBuffer) buffer.flip()).toString();
    }

    private OpsDeploymentStatusDTO parse(String response) {
        Map<String, String> values = new HashMap<>();
        for (String line : response.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        String state = values.get("state");
        if (!STATES.contains(state)) {
            return OpsDeploymentStatusDTO.unavailable();
        }
        String commit = values.get("commit");
        String version = values.get("version");
        return new OpsDeploymentStatusDTO(true, state, messageFor(state),
                version != null && VERSION.matcher(version).matches() ? version : null,
                commit != null && COMMIT.matcher(commit).matches() ? commit : null,
                values.get("updated_at"));
    }

    private String messageFor(String state) {
        return switch (state) {
            case "IDLE" -> "尚未请求部署。";
            case "QUEUED" -> "部署请求已接收。";
            case "RUNNING" -> "正在拉取代码并重建服务。";
            case "SUCCESS" -> "最近一次部署成功。";
            case "FAILED" -> "最近一次部署失败，请检查服务器部署日志。";
            case "BUSY" -> "已有部署任务正在运行。";
            case "REJECTED" -> "部署请求被拒绝。";
            default -> "部署服务暂不可用。";
        };
    }
}
