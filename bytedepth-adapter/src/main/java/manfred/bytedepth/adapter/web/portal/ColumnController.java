package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.series.GetSeriesForPortalQryExe;
import manfred.bytedepth.app.series.ListSeriesQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.NoSuchElementException;

@Controller
@RequestMapping("/columns")
@RequiredArgsConstructor
public class ColumnController {

    private final ListSeriesQryExe listSeriesQryExe;
    private final GetSeriesForPortalQryExe getSeriesForPortalQryExe;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page) {
        var result = listSeriesQryExe.execute(page);
        model.addAttribute("seriesList", result.series());
        model.addAttribute("currentPage", result.currentPage());
        model.addAttribute("totalPages", result.totalPages());
        model.addAttribute("total", result.total());
        model.addAttribute("pageSize", 10);
        return "public/columns/list";
    }

    @GetMapping("/{slug}")
    public String detail(@PathVariable String slug,
                         @RequestParam(defaultValue = "1") int page,
                         Model model) {
        try {
            var series = getSeriesForPortalQryExe.execute(slug, page);
            model.addAttribute("series", series);
            model.addAttribute("pageSize", 10);
            return "public/columns/detail";
        } catch (NoSuchElementException e) {
            throw e; // 触发全局 404 处理
        }
    }
}
