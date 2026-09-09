package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.project.ListProjectsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int PAGE_SIZE = 10;
    private static final int DISCOVERY_HOT_INTERVAL = 4;
    private static final int DISCOVERY_RECENT_LIMIT = 2;

    private final ListPostsQryExe listPostsQryExe;
    private final ListProjectsQryExe listProjectsQryExe;
    private final ListCategoriesQryExe listCategoriesQryExe;

    @GetMapping("/")
    public String home(@RequestParam(defaultValue = "1") int page,
                       @RequestParam(required = false) String sort,
                       Model model) {
        String normalizedSort = normalizeSort(sort);
        List<PostDTO> posts;
        int discoveryRecentCount = 0;
        if ("latest".equals(normalizedSort)) {
            posts = listPostsQryExe.execute(page, PAGE_SIZE);
        } else if ("hot".equals(normalizedSort)) {
            posts = listPostsQryExe.executeByHotness(page, PAGE_SIZE);
        } else {
            var discoveryCandidates = listPostsQryExe.executeLatestExcluding(List.of(), DISCOVERY_RECENT_LIMIT);
            var discoveryCandidateIds = discoveryCandidates.stream().map(PostDTO::getId).toList();
            var recentPosts = page == 1 ? discoveryCandidates : List.<PostDTO>of();
            var hotPosts = listPostsQryExe.executeByHotnessExcluding(discoveryCandidateIds, page, PAGE_SIZE);
            posts = interleaveDiscoveryPosts(hotPosts, recentPosts);
            model.addAttribute("discoveryNewPostIds", recentPosts.stream().map(PostDTO::getId).toList());
            discoveryRecentCount = discoveryCandidates.size();
        }
        long total = listPostsQryExe.countPublished();
        long totalPages = "discover".equals(normalizedSort)
                ? (Math.max(0, total - discoveryRecentCount) + PAGE_SIZE - 1) / PAGE_SIZE
                : (total + PAGE_SIZE - 1) / PAGE_SIZE;
        model.addAttribute("posts", posts);
        model.addAttribute("sort", normalizedSort);
        model.addAttribute("paginationBaseUrl", "/?sort=" + normalizedSort + "&");
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("total", total);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("hasPrev", page > 1);
        model.addAttribute("hasNext", page < totalPages);
        model.addAttribute("projects", listProjectsQryExe.execute());
        model.addAttribute("allCategories", listCategoriesQryExe.execute());
        return "public/index";
    }

    private String normalizeSort(String sort) {
        return "latest".equals(sort) || "hot".equals(sort) ? sort : "discover";
    }

    private List<PostDTO> interleaveDiscoveryPosts(List<PostDTO> hotPosts, List<PostDTO> recentPosts) {
        List<PostDTO> discoveryPosts = new ArrayList<>(hotPosts.size() + recentPosts.size());
        int recentIndex = 0;
        for (int hotIndex = 0; hotIndex < hotPosts.size(); hotIndex++) {
            discoveryPosts.add(hotPosts.get(hotIndex));
            if ((hotIndex + 1) % DISCOVERY_HOT_INTERVAL == 0 && recentIndex < recentPosts.size()) {
                discoveryPosts.add(recentPosts.get(recentIndex++));
            }
        }
        discoveryPosts.addAll(recentPosts.subList(recentIndex, recentPosts.size()));
        return discoveryPosts;
    }
}
