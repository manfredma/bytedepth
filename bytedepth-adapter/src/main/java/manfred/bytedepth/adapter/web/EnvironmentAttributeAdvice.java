package manfred.bytedepth.adapter.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 注入部署环境标识到所有 Thymeleaf 模型，供视觉区分生产与 staging。 */
@ControllerAdvice
public class EnvironmentAttributeAdvice {

    private final String environment;

    public EnvironmentAttributeAdvice(
            @Value("${bytedepth.environment:production}") String environment) {
        this.environment = (environment == null || environment.isBlank())
                ? "production" : environment;
    }

    @ModelAttribute
    public void addEnvironmentAttribute(Model model) {
        model.addAttribute("environment", environment);
    }
}
