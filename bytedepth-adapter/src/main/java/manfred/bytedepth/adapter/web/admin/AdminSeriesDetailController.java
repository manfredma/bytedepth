package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.series.AppendPostToSeriesCmdExe;
import manfred.bytedepth.app.series.CandidatePostDTO;
import manfred.bytedepth.app.series.GetSeriesDetailQryExe;
import manfred.bytedepth.app.series.MovePostInSeriesCmdExe;
import manfred.bytedepth.app.series.RemovePostFromSeriesCmdExe;
import manfred.bytedepth.app.series.SeriesDetailDTO;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/series")
@RequiredArgsConstructor
public class AdminSeriesDetailController {

    private final GetSeriesDetailQryExe getSeriesDetailQryExe;
    private final AppendPostToSeriesCmdExe appendPostToSeriesCmdExe;
    private final RemovePostFromSeriesCmdExe removePostFromSeriesCmdExe;
    private final MovePostInSeriesCmdExe movePostInSeriesCmdExe;
    private final SeriesRepository seriesRepository;

    private static final int CANDIDATE_PAGE_SIZE = 10;

    @GetMapping("/{slug}")
    public String detail(@PathVariable String slug,
                         @RequestParam(defaultValue = "1") int candidatePage,
                         @RequestParam(defaultValue = "") String q,
                         Model model) {
        SeriesDetailDTO series = getSeriesDetailQryExe.execute(slug);
        model.addAttribute("series", series);

        Long seriesId = series.getId();
        long total = seriesRepository.countCandidatesForSeries(seriesId, q);
        long totalPages = Math.max(1, (total + CANDIDATE_PAGE_SIZE - 1) / CANDIDATE_PAGE_SIZE);
        List<SeriesPostItem> candidates = seriesRepository.findCandidatesForSeries(
                seriesId, q, candidatePage, CANDIDATE_PAGE_SIZE);
        List<CandidatePostDTO> candidateDTOs = candidates.stream().map(item -> {
            CandidatePostDTO dto = new CandidatePostDTO();
            dto.setId(item.id());
            dto.setTitle(item.title());
            dto.setStatus(item.status() != null ? item.status() : "");
            return dto;
        }).collect(Collectors.toList());

        model.addAttribute("candidates", candidateDTOs);
        model.addAttribute("candidatePage", candidatePage);
        model.addAttribute("candidateTotalPages", totalPages);
        model.addAttribute("candidateTotal", total);
        model.addAttribute("candidatePageSize", CANDIDATE_PAGE_SIZE);
        model.addAttribute("q", q);
        return "admin/series/detail";
    }

    /** 移入文章（追加到末尾） */
    @PostMapping("/{slug}/posts")
    public String appendPost(@PathVariable String slug,
                             @RequestParam Long postId,
                             @RequestParam(defaultValue = "1") int candidatePage,
                             @RequestParam(defaultValue = "") String q) {
        SeriesDetailDTO series = getSeriesDetailQryExe.execute(slug);
        appendPostToSeriesCmdExe.execute(postId, series.getId());
        return "redirect:/admin/series/" + slug + "?candidatePage=" + candidatePage + "&q=" + q;
    }

    /** 移出文章 */
    @PostMapping("/{slug}/posts/{postId}/remove")
    public String removePost(@PathVariable String slug, @PathVariable Long postId) {
        removePostFromSeriesCmdExe.execute(postId);
        return "redirect:/admin/series/" + slug;
    }

    /** 上移 */
    @PostMapping("/{slug}/posts/{postId}/up")
    public String moveUp(@PathVariable String slug, @PathVariable Long postId) {
        SeriesDetailDTO series = getSeriesDetailQryExe.execute(slug);
        movePostInSeriesCmdExe.execute(series.getId(), postId, MovePostInSeriesCmdExe.Direction.UP);
        return "redirect:/admin/series/" + slug;
    }

    /** 下移 */
    @PostMapping("/{slug}/posts/{postId}/down")
    public String moveDown(@PathVariable String slug, @PathVariable Long postId) {
        SeriesDetailDTO series = getSeriesDetailQryExe.execute(slug);
        movePostInSeriesCmdExe.execute(series.getId(), postId, MovePostInSeriesCmdExe.Direction.DOWN);
        return "redirect:/admin/series/" + slug;
    }
}
