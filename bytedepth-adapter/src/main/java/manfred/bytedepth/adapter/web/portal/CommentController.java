package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;

@Controller
@RequestMapping("/posts/{postSlug}/comments")
@RequiredArgsConstructor
public class CommentController {

    private final SubmitCommentCmdExe submitCommentCmdExe;
    private final PostRepository postRepository;

    @PostMapping
    @PreAuthorize("hasAuthority('blog:comment:create')")
    public String submit(@PathVariable("postSlug") String postSlug,
                         @RequestParam("content") String content,
                         @AuthenticationPrincipal UserDetails currentUser) {
        Long postId = postRepository.findBySlug(postSlug)
                .orElseThrow(() -> new NoSuchElementException("文章不存在：" + postSlug))
                .getId();
        submitCommentCmdExe.execute(postId, currentUser.getUsername(), content);
        return "redirect:/posts/" + postSlug + "#comments";
    }
}
