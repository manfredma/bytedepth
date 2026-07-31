package manfred.bytedepth.app.analytics;

import manfred.bytedepth.domain.stats.PostViewedEvent;

/** Issues and validates the short-lived token required to update reading progress. */
public interface ReadingProgressTokenPort {
    void issue(PostViewedEvent event);

    boolean belongsToPost(String token, Long postId);
}
