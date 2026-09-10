package manfred.bytedepth.integration;

import manfred.bytedepth.BytedepthApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

class PostRepositoryITConfigurationTest {

    @Test
    void usesTheApplicationConfigurationExplicitly() {
        SpringBootTest configuration = PostRepositoryIT.class.getAnnotation(SpringBootTest.class);

        assertThat(configuration).isNotNull();
        assertThat(configuration.classes()).containsExactly(BytedepthApplication.class);
    }
}
