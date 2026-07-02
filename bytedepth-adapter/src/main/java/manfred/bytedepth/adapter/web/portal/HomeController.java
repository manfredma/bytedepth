package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.project.ListProjectsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int PAGE_SIZE = 10;

    private final ListPostsQryExe listPostsQryExe;
    private final ListProjectsQryExe listProjectsQryExe;
    private final ListCategoriesQryExe listCategoriesQryExe;

    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "1") int page, Model model) {
        var posts = listPostsQryExe.execute(page, PAGE_SIZE);
        long total = listPostsQryExe.countPublished();
        long totalPages = (total + PAGE_SIZE - 1) / PAGE_SIZE;
        model.addAttribute("posts", posts);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("total", total);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("hasPrev", page > 1);
        model.addAttribute("hasNext", page < totalPages);
        model.addAttribute("projects", listProjectsQryExe.execute());
        model.addAttribute("allCategories", listCategoriesQryExe.execute());
        return "public/index";
    }
}
