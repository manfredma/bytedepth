package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.SecurityUtils;
import manfred.bytedepth.app.annotation.CreateAnnotationCmdExe;
import manfred.bytedepth.app.annotation.DeleteAnnotationCmdExe;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.app.annotation.PostAnnotationDTO;
import manfred.bytedepth.app.annotation.UpdateAnnotationCmdExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.PatchMapping;

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
    private final UpdateAnnotationCmdExe updateAnnotationCmdExe;
    private final AnnotationVisitorIdentity visitorIdentity;

    /** 列出公开批注以及当前读者自己的私有划线。 */
    @GetMapping
    @ResponseBody
    public List<PostAnnotationDTO> list(@PathVariable("postSlug") String postSlug,
                                        @AuthenticationPrincipal UserDetails currentUser,
                                        HttpServletRequest request) {
        Long postId = resolvePostId(postSlug);
        Long userId = SecurityUtils.extractUserId(currentUser);
        String ownerTokenHash = visitorIdentity.existingHash(request);
        return listAnnotationsQryExe.execute(postId, userId, ownerTokenHash).stream()
                .map(annotation -> PostAnnotationDTO.from(annotation, userId, ownerTokenHash)).toList();
    }

    /** 创建划线或批注；匿名读者以浏览器身份 Cookie 作为私有归属。 */
    @PostMapping
    @ResponseBody
    public PostAnnotationDTO create(@PathVariable("postSlug") String postSlug,
                                    @RequestBody CreateAnnotationRequest request,
                                    @AuthenticationPrincipal UserDetails currentUser,
                                    HttpServletRequest servletRequest,
                                    HttpServletResponse servletResponse) {
        Long postId = resolvePostId(postSlug);
        Long userId = SecurityUtils.extractUserId(currentUser);
        String ownerTokenHash = userId == null ? visitorIdentity.getOrCreateHash(servletRequest, servletResponse) : null;
        var created = createAnnotationCmdExe.execute(
                postId,
                userId, ownerTokenHash,
                request.selectedText(),
                request.annotationText(),
                request.color(),
                request.visibility(),
                request.startOffset(),
                request.endOffset());
        return PostAnnotationDTO.from(created, userId, ownerTokenHash);
    }

    /** 删除批注（仅当前登录用户或当前匿名浏览器身份）。 */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> delete(@PathVariable("postSlug") String postSlug,
                                       @PathVariable Long id,
                                       @AuthenticationPrincipal UserDetails currentUser,
                                       HttpServletRequest request) {
        deleteAnnotationCmdExe.execute(id, resolvePostId(postSlug), SecurityUtils.extractUserId(currentUser), visitorIdentity.existingHash(request));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    @ResponseBody
    public PostAnnotationDTO update(@PathVariable("postSlug") String postSlug,
                                    @PathVariable Long id,
                                    @RequestBody UpdateAnnotationRequest request,
                                    @AuthenticationPrincipal UserDetails currentUser,
                                    HttpServletRequest servletRequest) {
        Long postId = resolvePostId(postSlug);
        Long userId = SecurityUtils.extractUserId(currentUser);
        String ownerTokenHash = visitorIdentity.existingHash(servletRequest);
        var updated = updateAnnotationCmdExe.execute(id, postId, userId, ownerTokenHash,
                request.annotationText(), request.visibility());
        return PostAnnotationDTO.from(updated, userId, ownerTokenHash);
    }

    private Long resolvePostId(String postSlug) {
        return postRepository.findBySlug(postSlug)
                .orElseThrow(() -> new NoSuchElementException("文章不存在：" + postSlug))
                .getId();
    }

    public record CreateAnnotationRequest(String selectedText, String annotationText,
                                          String color, AnnotationVisibility visibility,
                                          int startOffset, int endOffset) {
    }

    public record UpdateAnnotationRequest(String annotationText, AnnotationVisibility visibility) {
    }
}
