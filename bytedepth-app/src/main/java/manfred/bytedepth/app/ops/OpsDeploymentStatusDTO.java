package manfred.bytedepth.app.ops;

/** Safe, fixed deployment status; it intentionally contains no host command output. */
public record OpsDeploymentStatusDTO(boolean available, String state, String message,
                                     String commit, String updatedAt) {

    public static OpsDeploymentStatusDTO unavailable() {
        return new OpsDeploymentStatusDTO(false, "UNAVAILABLE",
                "部署服务暂不可用。", null, null);
    }
}
