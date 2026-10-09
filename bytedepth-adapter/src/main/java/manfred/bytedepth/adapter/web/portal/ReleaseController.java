package manfred.bytedepth.adapter.web.portal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ReleaseController {

    private final MarkdownRenderer markdownRenderer;

    public ReleaseController(MarkdownRenderer markdownRenderer) {
        this.markdownRenderer = markdownRenderer;
    }

    @GetMapping("/releases")
    public String releases(Model model) throws IOException {
        ClassPathResource resource = new ClassPathResource("release/CHANGELOG.md");
        try (var input = resource.getInputStream()) {
            model.addAttribute(
                    "releaseNotesHtml",
                    markdownRenderer.render(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        return "public/releases";
    }
}
