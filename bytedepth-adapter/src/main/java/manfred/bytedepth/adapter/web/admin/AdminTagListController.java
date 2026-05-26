package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class AdminTagListController {

    private final ListTagsQryExe listTagsQryExe;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tags", listTagsQryExe.findAllWithCount());
        return "admin/tags/list";
    }
}
