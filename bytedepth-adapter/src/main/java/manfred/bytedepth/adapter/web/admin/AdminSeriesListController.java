package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@PreAuthorize("hasAuthority(\'admin:dashboard:view\')")
@Controller
@RequestMapping("/admin/series")
@RequiredArgsConstructor
public class AdminSeriesListController {

    private final SeriesRepository seriesRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("seriesList", seriesRepository.findAll());
        return "admin/series/list";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam String slug,
                         @RequestParam(required = false) String description) {
        seriesRepository.findBySlug(slug).orElseGet(
                () -> seriesRepository.save(Series.create(name, slug,
                        description != null && !description.isBlank() ? description : null))
        );
        return "redirect:/admin/series";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        seriesRepository.deleteWithPosts(id);
        return "redirect:/admin/series";
    }
}
