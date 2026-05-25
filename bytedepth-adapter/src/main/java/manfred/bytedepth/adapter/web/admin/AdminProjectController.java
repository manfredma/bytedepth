package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.project.CreateProjectCmdExe;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/projects")
@RequiredArgsConstructor
public class AdminProjectController {

    private final CreateProjectCmdExe createProjectCmdExe;

    @GetMapping("/new")
    public String newForm() {
        return "admin/projects/edit";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String techStack,
                         @RequestParam(required = false) String githubUrl,
                         @RequestParam(required = false) String demoUrl,
                         @RequestParam(defaultValue = "0") int sortOrder) {
        createProjectCmdExe.execute(name, description, techStack, githubUrl, demoUrl, sortOrder);
        return "redirect:/projects";
    }
}
