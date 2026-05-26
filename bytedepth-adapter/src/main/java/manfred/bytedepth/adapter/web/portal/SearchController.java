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
    public String search(@RequestParam(defaultValue = "") String q,
                         @RequestParam(defaultValue = "1") int page,
                         Model model) {
        var result = searchPostsQryExe.execute(q, page);
        model.addAttribute("q", q);
        model.addAttribute("results", result.getHits());
        model.addAttribute("totalHits", result.getTotalHits());
        model.addAttribute("currentPage", result.getPage());
        model.addAttribute("totalPages", result.totalPages());
        model.addAttribute("hasPrev", result.hasPrev());
        model.addAttribute("hasNext", result.hasNext());
        return "public/search";
    }
}
