package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ListPostsQryExe listPostsQryExe;

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("recentPosts", listPostsQryExe.execute(1, 5));
        return "public/index";
    }
}
