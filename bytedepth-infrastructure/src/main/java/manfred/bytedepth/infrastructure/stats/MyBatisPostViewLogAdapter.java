package manfred.bytedepth.infrastructure.stats;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.analytics.PostViewLogDTO;
import manfred.bytedepth.app.analytics.PostViewLogPort;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MyBatisPostViewLogAdapter implements PostViewLogPort {

    private final PostViewLogMapper mapper;

    @Override
    public void upsertReadingProgress(Long postId, String visitToken, int activeReadSeconds, int maxScrollDepth,
                                      boolean completed) {
        mapper.upsertReadingProgress(postId, visitToken, activeReadSeconds, maxScrollDepth, completed);
    }

    @Override
    public List<PostViewLogDTO> findPage(Long postId, Long userId, int offset, int size) {
        return mapper.findPage(postId, userId, offset, size);
    }

    @Override
    public long countPage(Long postId, Long userId) {
        return mapper.countPage(postId, userId);
    }
}
