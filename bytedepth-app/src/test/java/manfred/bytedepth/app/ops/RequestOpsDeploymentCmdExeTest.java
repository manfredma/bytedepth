package manfred.bytedepth.app.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestOpsDeploymentCmdExeTest {

    @Test
    void delegatesTheRequestedReleaseVersionToTheDeploymentPort() {
        OpsDeploymentPort port = mock(OpsDeploymentPort.class);
        OpsDeploymentStatusDTO expected = new OpsDeploymentStatusDTO(true, "QUEUED", "queued", "v1.0.0", null, null);
        when(port.deployRelease("v1.0.0")).thenReturn(expected);

        OpsDeploymentStatusDTO actual = new RequestOpsDeploymentCmdExe(port).execute("v1.0.0");

        assertSame(expected, actual);
        verify(port).deployRelease("v1.0.0");
    }
}
