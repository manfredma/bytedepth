package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.security.ContentOwnershipGuard;
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

@PreAuthorize("hasAnyAuthority('admin:dashboard:view', 'blog:series:create:own', 'blog:series:edit:own')")
@Controller
@RequestMapping("/admin/series")
@RequiredArgsConstructor
public class AdminSeriesListController {

    private final SeriesRepository seriesRepository;
    private final ContentOwnershipGuard contentOwnershipGuard;

    @GetMapping
    public String list(Authentication authentication, Model model) {
        model.addAttribute("seriesList", contentOwnershipGuard.canManageSeries(authentication)
                ? seriesRepository.findAll()
                : seriesRepository.findByAuthorId(contentOwnershipGuard.currentUserId(authentication)));
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
