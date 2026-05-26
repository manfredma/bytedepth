package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.search.SearchPostsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchPostsQryExe searchPostsQryExe;

    @GetMapping
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("q", q);
        model.addAttribute("results", searchPostsQryExe.execute(q));
        return "public/search";
    }
}
