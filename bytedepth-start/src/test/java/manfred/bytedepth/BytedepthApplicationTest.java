package manfred.bytedepth;

import manfred.bytedepth.app.analytics.ArchiveViewLogsCmdExe;
import manfred.bytedepth.app.analytics.ViewLogArchivePort;
import manfred.bytedepth.app.analytics.ViewLogRetentionPolicy;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;

class BytedepthApplicationTest {

    @Test
    void mainStartsTheSpringApplicationWithItsArguments() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            BytedepthApplication.main(new String[]{"--spring.main.web-application-type=none"});

            springApplication.verify(() -> SpringApplication.run(BytedepthApplication.class,
                    new String[]{"--spring.main.web-application-type=none"}));
        }
    }

    @Test
    void archiveUseCaseBeanIsBuiltFromItsPortAndRetentionPolicy() {
        var port = mock(ViewLogArchivePort.class);
        var policy = new ViewLogRetentionPolicy(7, ZoneId.of("Asia/Shanghai"));

        assertNotNull(new BytedepthApplication().archiveViewLogsCmdExe(port, policy));
    }
}
