package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.security.ContentOwnershipGuard;
import manfred.bytedepth.adapter.web.filter.FilterField;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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

@PreAuthorize("hasAnyAuthority('admin:dashboard:view', 'blog:series:create:own', 'blog:series:edit:own')")
@Controller
@RequestMapping("/admin/series")
@RequiredArgsConstructor
public class AdminSeriesListController {

    private final SeriesRepository seriesRepository;
    private final ContentOwnershipGuard contentOwnershipGuard;

    @GetMapping
    public String list(Authentication authentication, Model model,
                       @RequestParam(required = false) String name,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        boolean canManage = contentOwnershipGuard.canManageSeries(authentication);
        Long userId = canManage ? null : contentOwnershipGuard.currentUserId(authentication);
        List<Series> records = canManage ? seriesRepository.findPage(name, page, size) : seriesRepository.findPageByAuthorId(userId, name, page, size);
        long total = canManage ? seriesRepository.count(name) : seriesRepository.countByAuthorId(userId, name);
        model.addAttribute("seriesList", records);
        model.addAttribute("currentPage", page); model.addAttribute("totalPages", (int) Math.ceil((double) total / size));
        model.addAttribute("total", total); model.addAttribute("pageSize", size);
        model.addAttribute("filterFields", List.of(FilterField.text("name", "名称", name == null ? "" : name, "输入名称")));
        model.addAttribute("filterBaseUrl", "/admin/series?" + (name == null || name.isBlank() ? "" : "name=" + UriUtils.encodeQueryParam(name, StandardCharsets.UTF_8) + "&"));
        return "admin/series/list";
    }

    @PostMapping
    public String create(Authentication authentication, @RequestParam String name,
                         @RequestParam String slug,
                         @RequestParam(required = false) String description) {
        if (seriesRepository.findBySlug(slug).isEmpty()) {
            seriesRepository.save(Series.create(name, slug, description != null && !description.isBlank() ? description : null,
                    contentOwnershipGuard.currentUserId(authentication)));
        }
        return "redirect:/admin/series";
    }

    @PostMapping("/{id}/delete")
    public String delete(Authentication authentication, @PathVariable Long id) {
        contentOwnershipGuard.requireSeriesOwner(authentication, id);
        seriesRepository.deleteWithPosts(id);
        return "redirect:/admin/series";
    }
}
