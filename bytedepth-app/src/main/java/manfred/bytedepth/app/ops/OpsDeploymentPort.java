package manfred.bytedepth.app.ops;

/** Fixed deployment actions exposed by the host-side deployment service. */
public interface OpsDeploymentPort {

    OpsDeploymentStatusDTO status();

    OpsDeploymentStatusDTO deployMain();
}
