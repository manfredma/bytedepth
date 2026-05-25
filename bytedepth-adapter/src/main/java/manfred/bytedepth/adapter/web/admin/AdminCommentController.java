package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.comment.ReviewCommentCmdExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/comments")
@RequiredArgsConstructor
public class AdminCommentController {

    private final ListCommentsQryExe listCommentsQryExe;
    private final ReviewCommentCmdExe reviewCommentCmdExe;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("comments", listCommentsQryExe.findPending());
        return "admin/comments/list";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable("id") Long id) {
        reviewCommentCmdExe.approve(id);
        return "redirect:/admin/comments";
    }

    @PostMapping("/{id}/reject")
    public String reject(@PathVariable("id") Long id) {
        reviewCommentCmdExe.reject(id);
        return "redirect:/admin/comments";
    }
}
