package manfred.bytedepth;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

import static org.mockito.Mockito.mockStatic;

class BytedepthApplicationTest {

    @Test
    void mainStartsTheSpringApplicationWithItsArguments() {
        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            BytedepthApplication.main(new String[]{"--spring.main.web-application-type=none"});

            springApplication.verify(() -> SpringApplication.run(BytedepthApplication.class,
                    new String[]{"--spring.main.web-application-type=none"}));
        }
    }
}
