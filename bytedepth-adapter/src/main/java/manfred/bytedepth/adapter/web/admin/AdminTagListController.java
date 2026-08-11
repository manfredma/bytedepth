package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.tag.DeleteTagCmdExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.adapter.web.filter.FilterField;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@PreAuthorize("hasAuthority(\'admin:dashboard:view\')")
@Controller
@RequestMapping("/admin/tags")
@RequiredArgsConstructor
public class AdminTagListController {

    private final ListTagsQryExe listTagsQryExe;
    private final DeleteTagCmdExe deleteTagCmdExe;

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String name,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        var result = listTagsQryExe.findPageWithCount(name, page, size);
        model.addAttribute("tags", result.records());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", (int) Math.ceil((double) result.total() / size));
        model.addAttribute("total", result.total());
        model.addAttribute("pageSize", size);
        model.addAttribute("filterFields", List.of(FilterField.text("name", "名称", name == null ? "" : name, "输入名称")));
        String baseUrl = "/admin/tags?" + (name == null || name.isBlank() ? "" : "name=" + UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8) + "&");
        model.addAttribute("filterBaseUrl", baseUrl);
        return "admin/tags/list";
    }

    @PostMapping("/{tagId}/delete")
    public String delete(@PathVariable Long tagId) {
        deleteTagCmdExe.execute(tagId);
        return "redirect:/admin/tags";
    }
}
