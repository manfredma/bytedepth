package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.category.CreateCategoryCmdExe;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@PreAuthorize("hasAuthority(\'admin:dashboard:view\')")
@Controller
@RequestMapping("/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final ListCategoriesQryExe listCategoriesQryExe;
    private final CreateCategoryCmdExe createCategoryCmdExe;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("categories", listCategoriesQryExe.execute());
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
