package manfred.bytedepth.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

class EnvironmentAttributeAdviceTest {

    @Test
    void injectsConfiguredEnvironment() {
        Model model = new ConcurrentModel();
        EnvironmentAttributeAdvice advice = new EnvironmentAttributeAdvice("staging");

        advice.addEnvironmentAttribute(model);

        assertThat(model.getAttribute("environment")).isEqualTo("staging");
    }

    @Test
    void defaultsToProductionWhenNull() {
        Model model = new ConcurrentModel();
        EnvironmentAttributeAdvice advice = new EnvironmentAttributeAdvice(null);

        advice.addEnvironmentAttribute(model);

        assertThat(model.getAttribute("environment")).isEqualTo("production");
    }

    @Test
    void defaultsToProductionWhenBlank() {
        Model model = new ConcurrentModel();
        EnvironmentAttributeAdvice advice = new EnvironmentAttributeAdvice("  ");

        advice.addEnvironmentAttribute(model);

        assertThat(model.getAttribute("environment")).isEqualTo("production");
    }
}
