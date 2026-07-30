package manfred.bytedepth.infrastructure.ops;

import manfred.bytedepth.app.ops.OpsDeploymentStatusDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UnixSocketOpsDeploymentAdapterTest {

    @Test
    void blankSocketPathDoesNotAttemptDeployment() {
        UnixSocketOpsDeploymentAdapter adapter = new UnixSocketOpsDeploymentAdapter("");

        OpsDeploymentStatusDTO status = adapter.deployMain();

        assertFalse(status.available());
        assertEquals("UNAVAILABLE", status.state());
    }
}
