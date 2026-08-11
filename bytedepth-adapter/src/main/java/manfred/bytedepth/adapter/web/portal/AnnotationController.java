package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.SecurityUtils;
import manfred.bytedepth.app.annotation.CreateAnnotationCmdExe;
import manfred.bytedepth.app.annotation.DeleteAnnotationCmdExe;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.app.annotation.PostAnnotationDTO;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.NoSuchElementException;

@Controller
@RequestMapping("/posts/{postSlug}/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private final PostRepository postRepository;
    private final ListAnnotationsQryExe listAnnotationsQryExe;
    private final CreateAnnotationCmdExe createAnnotationCmdExe;
    private final DeleteAnnotationCmdExe deleteAnnotationCmdExe;

    /** 列出文章批注（公开，访客可见）。 */
    @GetMapping
    @ResponseBody
    public List<PostAnnotationDTO> list(@PathVariable("postSlug") String postSlug) {
        Long postId = resolvePostId(postSlug);
        return listAnnotationsQryExe.execute(postId).stream()
                .map(PostAnnotationDTO::from).toList();
    }

    /** 创建批注（需登录）。 */
    @PostMapping
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public PostAnnotationDTO create(@PathVariable("postSlug") String postSlug,
                                    @RequestBody CreateAnnotationRequest request,
                                    @AuthenticationPrincipal UserDetails currentUser) {
        Long postId = resolvePostId(postSlug);
        var created = createAnnotationCmdExe.execute(
                postId,
                SecurityUtils.extractUserId(currentUser),
                request.selectedText(),
                request.annotationText(),
                request.color(),
                request.startOffset(),
                request.endOffset());
        return PostAnnotationDTO.from(created);
    }

    /** 删除批注（需登录，仅作者）。 */
    @DeleteMapping("/{id}")
    @ResponseBody
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> delete(@PathVariable("postSlug") String postSlug,
                                       @PathVariable Long id,
                                       @AuthenticationPrincipal UserDetails currentUser) {
        deleteAnnotationCmdExe.execute(id, SecurityUtils.extractUserId(currentUser));
        return ResponseEntity.noContent().build();
    }

    private Long resolvePostId(String postSlug) {
        return postRepository.findBySlug(postSlug)
                .orElseThrow(() -> new NoSuchElementException("文章不存在：" + postSlug))
                .getId();
    }

    public record CreateAnnotationRequest(String selectedText, String annotationText,
                                          String color, int startOffset, int endOffset) {
    }
}
