package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/posts/{postId}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final SubmitCommentCmdExe submitCommentCmdExe;

    @PostMapping
    public String submit(@PathVariable("postId") Long postId,
                         @RequestParam("authorName") String authorName,
                         @RequestParam(value = "authorEmail", required = false) String authorEmail,
                         @RequestParam("content") String content) {
        submitCommentCmdExe.execute(postId, authorName, authorEmail, content);
        return "redirect:/posts/" + postId + "?commented=1";
    }
}
