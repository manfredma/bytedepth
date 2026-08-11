package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.filter.FilterField;
import manfred.bytedepth.app.category.CreateCategoryCmdExe;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@PreAuthorize("hasAuthority(\'admin:dashboard:view\')")
@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final ListCategoriesQryExe listCategoriesQryExe;
    private final CreateCategoryCmdExe createCategoryCmdExe;

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) String name,
                       @RequestParam(required = false) String slug) {
        model.addAttribute("categories", listCategoriesQryExe.executeFiltered(name, slug));
        model.addAttribute("filterFields", List.of(
                FilterField.text("name", "名称", name == null ? "" : name, "输入名称"),
                FilterField.text("slug", "Slug", slug == null ? "" : slug, "输入 Slug")));
        model.addAttribute("filterBaseUrl", "/admin/categories?");
        return "admin/categories/list";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam String slug,
                         @RequestParam(required = false) Long parentId) {
        createCategoryCmdExe.execute(name, slug, parentId);
        return "redirect:/admin/categories";
    }
}
