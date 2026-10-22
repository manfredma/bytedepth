package manfred.bytedepth.adapter.web.mobile;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.interaction.MobileInteractionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/v1/posts/{postId}/comments")
@RequiredArgsConstructor
public class MobileCommentController {
    private final MobileInteractionService interactions;

    @GetMapping
    public MobilePage<CommentResponse> list(
            @PathVariable Long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        MobilePage.validate(page, pageSize);
        var result = interactions.comments(postId, page, pageSize);
        return MobilePage.of(
                result.items().stream()
                        .map(c -> new CommentResponse(
                                c.getId().toString(),
                                c.getPostId().toString(),
                                c.getContent(),
                                c.getAuthorName(),
                                MobileAnnotationResponseMapper.utc(c.getCreatedAt())))
                        .toList(),
                page,
                pageSize,
                result.total());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('blog:comment:create')")
    public org.springframework.http.ResponseEntity<Void> submit(
            @PathVariable Long postId, @RequestBody CommentRequest request, @AuthenticationPrincipal UserDetails user) {
        interactions.comment(postId, user.getUsername(), request.content());
        return org.springframework.http.ResponseEntity.noContent().build();
    }

    public record CommentRequest(String content) {}

    public record CommentResponse(String id, String postId, String content, String username, String createdAt) {}
}
