package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.project.ListProjectsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ListPostsQryExe listPostsQryExe;
    private final ListProjectsQryExe listProjectsQryExe;
    private final ListCategoriesQryExe listCategoriesQryExe;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("recentPosts", listPostsQryExe.execute(1, 5));
        model.addAttribute("projects", listProjectsQryExe.execute());
        model.addAttribute("allCategories", listCategoriesQryExe.execute());
        return "public/index";
    }
}
