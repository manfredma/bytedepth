package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.filter.FilterField;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final ListCommentsQryExe listCommentsQryExe;

    @GetMapping
    @PreAuthorize("hasAuthority('admin:dashboard:view')")
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "50") int size,
                       @RequestParam(required = false) String authorName,
                       @RequestParam(required = false) Long postId) {
        var result = listCommentsQryExe.findPage(page, size, authorName, postId);
        int totalPages = (int) Math.ceil((double) result.total() / size);
        model.addAttribute("comments", result.comments());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("total", result.total());
        model.addAttribute("pageSize", size);
        model.addAttribute("filterFields", List.of(
                FilterField.text("authorName", "作者名", authorName == null ? "" : authorName, "输入作者名"),
                FilterField.number("postId", "文章 ID", postId == null ? "" : String.valueOf(postId), "数字")));
        model.addAttribute("filterBaseUrl", buildFilterBaseUrl(authorName, postId));
        return "admin/comments/list";
    }

    private String buildFilterBaseUrl(String authorName, Long postId) {
        StringBuilder b = new StringBuilder("/admin/comments?");
        if (authorName != null && !authorName.isBlank()) {
            b.append("authorName=").append(UriUtils.encodeQueryParam(authorName.trim(), StandardCharsets.UTF_8)).append('&');
        }
        if (postId != null) {
            b.append("postId=").append(postId).append('&');
        }
        return b.toString();
    }
}
