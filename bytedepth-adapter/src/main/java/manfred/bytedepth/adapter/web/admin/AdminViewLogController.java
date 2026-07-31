package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.analytics.PostViewLogPort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 文章访问日志管理后台。
 * 路径：GET /admin/view-logs
 * 权限：由 SecurityConfig 中 /admin/** 规则守卫（需 admin:dashboard:view）。
 */
@Controller
@RequestMapping("/admin/view-logs")
@RequiredArgsConstructor
public class AdminViewLogController {

    private static final int PAGE_SIZE = 20;

    private final PostViewLogPort postViewLogPort;

    @GetMapping
    public String list(Model model,
                       @RequestParam(required = false) Long postId,
                       @RequestParam(required = false) Long userId,
                       @RequestParam(defaultValue = "1") int page) {
        int offset = (page - 1) * PAGE_SIZE;
        var logs = postViewLogPort.findPage(postId, userId, offset, PAGE_SIZE);
        long total = postViewLogPort.countPage(postId, userId);
        int totalPages = (int) Math.max(1, Math.ceil((double) total / PAGE_SIZE));

        model.addAttribute("logs", logs);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("total", total);
        model.addAttribute("pageSize", PAGE_SIZE);
        model.addAttribute("filterPostId", postId);
        model.addAttribute("filterUserId", userId);
        return "admin/view-logs/list";
    }
}
