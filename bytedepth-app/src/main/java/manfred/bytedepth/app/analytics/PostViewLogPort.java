package manfred.bytedepth.app.analytics;

import java.time.LocalDateTime;
import java.util.List;

/** Application boundary for recording and querying post-view logs. */
public interface PostViewLogPort {
    void upsertReadingProgress(Long postId, String visitToken, int activeReadSeconds, int maxScrollDepth,
                               boolean completed);

    List<PostViewLogDTO> findPage(Long postId, Long userId, LocalDateTime cutoff, int offset, int size);

    long countPage(Long postId, Long userId, LocalDateTime cutoff);
}
