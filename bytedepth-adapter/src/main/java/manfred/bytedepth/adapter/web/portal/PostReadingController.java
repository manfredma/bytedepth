package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.infrastructure.stats.PostViewLogMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostReadingController {

    private static final int MAX_ACTIVE_READ_SECONDS = 86_400;

    private final GetPostQryExe getPostQryExe;
    private final PostViewLogMapper postViewLogMapper;

    @PostMapping("/{slug}/reading-progress")
    public ResponseEntity<Void> recordProgress(@PathVariable String slug,
                                                @RequestBody ReadingProgressRequest request) {
        if (request.visitToken() == null || request.visitToken().length() > 64
                || request.activeReadSeconds() < 0 || request.activeReadSeconds() > MAX_ACTIVE_READ_SECONDS
                || request.maxScrollDepth() < 0 || request.maxScrollDepth() > 100) {
            return ResponseEntity.badRequest().build();
        }
        Long postId = getPostQryExe.executeBySlug(slug).getId();
        postViewLogMapper.upsertReadingProgress(postId, request.visitToken(), request.activeReadSeconds(),
                request.maxScrollDepth(), request.completed());
        return ResponseEntity.noContent().build();
    }

    public record ReadingProgressRequest(String visitToken, int activeReadSeconds,
                                         int maxScrollDepth, boolean completed) {
    }
}
