package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.project.ListProjectsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ListProjectsQryExe listProjectsQryExe;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("projects", listProjectsQryExe.execute());
        return "public/projects/list";
    }
}
