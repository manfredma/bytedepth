package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.adapter.web.util.SecurityUtils;
import manfred.bytedepth.app.annotation.AnnotationView;
import manfred.bytedepth.app.annotation.AnnotationWriteConflictException;
import manfred.bytedepth.app.annotation.CreateAnnotationCmdExe;
import manfred.bytedepth.app.annotation.CreateAnnotationRequest;
import manfred.bytedepth.app.annotation.DeleteAnnotationCmdExe;
import manfred.bytedepth.app.annotation.InvalidAnchorException;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.app.annotation.PostAnnotationDTO;
import manfred.bytedepth.app.annotation.UpdateAnnotationCmdExe;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/posts/{postSlug}/annotations")
@RequiredArgsConstructor
public class AnnotationController {

    private static final DateTimeFormatter ANNOTATION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final PostRepository postRepository;
    private final ListAnnotationsQryExe listAnnotationsQryExe;
    private final CreateAnnotationCmdExe createAnnotationCmdExe;
    private final DeleteAnnotationCmdExe deleteAnnotationCmdExe;
    private final UpdateAnnotationCmdExe updateAnnotationCmdExe;
    private final AnnotationVisitorIdentity visitorIdentity;
    private final MarkdownRenderer markdownRenderer;

    /** 列出公开批注以及当前读者自己的私有划线。 */
    @GetMapping
    @ResponseBody
    public List<AnnotationResponse> list(
            @PathVariable("postSlug") String postSlug,
            @AuthenticationPrincipal UserDetails currentUser,
            HttpServletRequest request) {
        Long postId = resolvePostId(postSlug, currentUser);
        Long userId = SecurityUtils.extractUserId(currentUser);
        String ownerTokenHash = visitorIdentity.existingHash(request);
        return listAnnotationsQryExe.views(postId, userId, ownerTokenHash).stream()
                .map(view -> response(view, userId, ownerTokenHash, markdownRenderer))
                .toList();
    }

    /** 创建划线或批注；匿名读者以浏览器身份 Cookie 作为私有归属。 */
    @PostMapping
    @ResponseBody
    public AnnotationResponse create(
            @PathVariable("postSlug") String postSlug,
            @RequestBody CreateAnnotationRequest request,
            @AuthenticationPrincipal UserDetails currentUser,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        Long postId = resolvePostId(postSlug, currentUser);
        Long userId = SecurityUtils.extractUserId(currentUser);
        String ownerTokenHash =
                userId == null ? visitorIdentity.getOrCreateHash(servletRequest, servletResponse) : null;
        var created = createAnnotationCmdExe.execute(postId, userId, ownerTokenHash, request);
        return response(created, userId, ownerTokenHash, markdownRenderer);
    }

    /** 删除批注（仅当前登录用户或当前匿名浏览器身份）。 */
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Void> delete(
            @PathVariable("postSlug") String postSlug,
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails currentUser,
            HttpServletRequest request) {
        deleteAnnotationCmdExe.execute(
                id,
                resolvePostId(postSlug, currentUser),
                SecurityUtils.extractUserId(currentUser),
                visitorIdentity.existingHash(request));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}")
    @ResponseBody
    public AnnotationResponse update(
            @PathVariable("postSlug") String postSlug,
            @PathVariable Long id,
            @RequestBody UpdateAnnotationRequest request,
            @AuthenticationPrincipal UserDetails currentUser,
            HttpServletRequest servletRequest) {
        Long postId = resolvePostId(postSlug, currentUser);
        Long userId = SecurityUtils.extractUserId(currentUser);
        String ownerTokenHash = visitorIdentity.existingHash(servletRequest);
        var updated = updateAnnotationCmdExe.execute(
                id, postId, userId, ownerTokenHash, request.annotationText(), request.visibility());
        return response(listAnnotationsQryExe.viewCurrent(updated), userId, ownerTokenHash, markdownRenderer);
    }

    private Long resolvePostId(String postSlug, UserDetails currentUser) {
        var post =
                postRepository.findBySlug(postSlug).orElseThrow(() -> new NoSuchElementException("文章不存在：" + postSlug));
        if (post.getStatus() == manfred.bytedepth.domain.post.PostStatus.DELETED
                || (post.getStatus() != manfred.bytedepth.domain.post.PostStatus.PUBLISHED
                        && !SecurityUtils.isOwner(currentUser, post.getAuthorId())
                        && !SecurityUtils.hasAuthority(currentUser, "blog:post:manage")))
            throw new NoSuchElementException("文章不存在：" + postSlug);
        return post.getId();
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(AnnotationWriteConflictException.class)
    public ResponseEntity<String> conflict(AnnotationWriteConflictException error) {
        return ResponseEntity.status(error.kind() == AnnotationWriteConflictException.Kind.DELETED_REQUEST ? 410 : 409)
                .body(error.kind().name());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler({
        InvalidAnchorException.class,
        org.springframework.http.converter.HttpMessageNotReadableException.class
    })
    public ResponseEntity<String> invalidSelector(Exception error) {
        return ResponseEntity.status(422).body("批注定位信息无效，请刷新文章后重新选择；已输入的评论可以保留。");
    }

    static AnnotationResponse response(
            AnnotationView view, Long userId, String ownerTokenHash, MarkdownRenderer renderer) {
        var annotation = PostAnnotationDTO.from(view.annotation(), userId, ownerTokenHash);
        return AnnotationResponse.from(annotation, renderer.render(annotation.annotationText()), view);
    }

    public record UpdateAnnotationRequest(String annotationText, AnnotationVisibility visibility) {}

    record AnnotationResponse(
            Long id,
            Long postId,
            String selectedText,
            String annotationText,
            String annotationHtml,
            String color,
            AnnotationVisibility visibility,
            int startOffset,
            int endOffset,
            String createdAt,
            boolean ownedByCurrentVisitor,
            manfred.bytedepth.domain.annotation.AnnotationAnchor anchor,
            manfred.bytedepth.domain.annotation.AnnotationPlacement placement,
            String targetDigest,
            Long migrationBaselineRevision) {
        private static AnnotationResponse from(
                PostAnnotationDTO annotation, String annotationHtml, AnnotationView view) {
            return new AnnotationResponse(
                    annotation.id(),
                    annotation.postId(),
                    annotation.selectedText(),
                    annotation.annotationText(),
                    annotationHtml,
                    annotation.color(),
                    annotation.visibility(),
                    annotation.startOffset(),
                    annotation.endOffset(),
                    annotation.createdAt().format(ANNOTATION_TIME_FORMAT),
                    annotation.ownedByCurrentVisitor(),
                    view.annotation().anchor(),
                    view.placement(),
                    view.targetDigest(),
                    view.annotation().migrationBaselineRevision());
        }
    }
}
