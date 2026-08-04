package manfred.bytedepth.app.ops;

import org.springframework.stereotype.Component;

@Component
public class RequestOpsDeploymentCmdExe {

    private final OpsDeploymentPort deploymentPort;

    public RequestOpsDeploymentCmdExe(OpsDeploymentPort deploymentPort) {
        this.deploymentPort = deploymentPort;
    }

    public OpsDeploymentStatusDTO execute(String version) {
        return deploymentPort.deployRelease(version);
    }
}
