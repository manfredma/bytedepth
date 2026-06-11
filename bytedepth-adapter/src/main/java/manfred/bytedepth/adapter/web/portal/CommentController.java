package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final SubmitCommentCmdExe submitCommentCmdExe;

    @PostMapping
    @PreAuthorize("hasAuthority('blog:comment:create')")
    public String submit(@PathVariable("postId") Long postId,
                         @RequestParam("content") String content,
                         @AuthenticationPrincipal UserDetails currentUser) {
        submitCommentCmdExe.execute(postId, currentUser.getUsername(), content);
        return "redirect:/posts/" + postId + "#comments";
    }
}
