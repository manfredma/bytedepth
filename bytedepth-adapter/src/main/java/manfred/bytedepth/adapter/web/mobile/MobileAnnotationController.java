package manfred.bytedepth.adapter.web.mobile;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.SecurityUtils;
import manfred.bytedepth.app.annotation.*;
import manfred.bytedepth.app.interaction.MobileInteractionService;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/v1")
@RequiredArgsConstructor
public class MobileAnnotationController {
    private final MobileInteractionService interactions;
    private final ListOwnAnnotationsQryExe own;
    private final MobileAnnotationResponseMapper mapper;

    @GetMapping("/posts/{postId}/annotations")
    public Envelope list(@PathVariable Long postId, @AuthenticationPrincipal UserDetails user) {
        Long userId = SecurityUtils.extractUserId(user);
        var result = interactions.annotations(postId, userId);
        var target = result.target();
        return new Envelope(
                1,
                postId.toString(),
                target.contentVersion(),
                target.document().projectionVersion(),
                target.document().digest(),
                result.annotations().stream()
                        .map(view -> mapper.map(view, userId))
                        .toList());
    }

    @PostMapping("/posts/{postId}/annotations")
    public ResponseEntity<MobileAnnotationResponseMapper.Response> create(
            @PathVariable Long postId,
            @RequestBody CreateAnnotationRequest request,
            @AuthenticationPrincipal UserDetails user) {
        Long userId = SecurityUtils.extractUserId(user);
        return ResponseEntity.status(201).body(mapper.map(interactions.create(postId, userId, request), userId));
    }

    @PatchMapping("/posts/{postId}/annotations/{annotationId}")
    public MobileAnnotationResponseMapper.Response update(
            @PathVariable Long postId,
            @PathVariable Long annotationId,
            @RequestBody UpdateRequest request,
            @AuthenticationPrincipal UserDetails user) {
        Long userId = SecurityUtils.extractUserId(user);
        return mapper.map(
                interactions.update(postId, annotationId, userId, request.annotationText(), request.visibility()),
                userId);
    }

    @DeleteMapping("/posts/{postId}/annotations/{annotationId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long postId, @PathVariable Long annotationId, @AuthenticationPrincipal UserDetails user) {
        interactions.delete(postId, annotationId, SecurityUtils.extractUserId(user));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/annotations")
    public MobilePage<MobileAnnotationResponseMapper.Response> own(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        MobilePage.validate(page, pageSize);
        Long userId = SecurityUtils.extractUserId(user);
        var result = own.execute(userId, page, pageSize);
        return MobilePage.of(
                result.items().stream().map(view -> mapper.map(view, userId)).toList(), page, pageSize, result.total());
    }

    @ExceptionHandler(AnnotationOwnershipException.class)
    public ResponseEntity<MobileApiError> forbidden(AnnotationOwnershipException error) {
        return ResponseEntity.status(403)
                .header("Cache-Control", "no-store")
                .body(new MobileApiError("FORBIDDEN", "请求被拒绝"));
    }

    public record UpdateRequest(String annotationText, AnnotationVisibility visibility) {}

    public record Envelope(
            int schemaVersion,
            String postId,
            long contentVersion,
            int projectionVersion,
            String digest,
            List<MobileAnnotationResponseMapper.Response> annotations) {}
}
