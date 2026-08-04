package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.tag.DeleteTagCmdExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@PreAuthorize("hasAuthority(\'admin:dashboard:view\')")
@Controller
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class AdminTagListController {

    private final ListTagsQryExe listTagsQryExe;
    private final DeleteTagCmdExe deleteTagCmdExe;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("tags", listTagsQryExe.findAllWithCount());
        return "admin/tags/list";
    }

    @PostMapping("/{tagId}/delete")
    public String delete(@PathVariable Long tagId) {
        deleteTagCmdExe.execute(tagId);
        return "redirect:/admin/tags";
    }
}
