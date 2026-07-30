package manfred.bytedepth.app.ops;

import org.springframework.stereotype.Component;

@Component
public class OpsDeploymentStatusQryExe {

    private final OpsDeploymentPort deploymentPort;

    public OpsDeploymentStatusQryExe(OpsDeploymentPort deploymentPort) {
        this.deploymentPort = deploymentPort;
    }

    public OpsDeploymentStatusDTO execute() {
        return deploymentPort.status();
    }
}
