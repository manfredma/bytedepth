package manfred.bytedepth.app.post;

import java.util.Optional;
import manfred.bytedepth.domain.post.PostContentRevision;

public interface PostContentRevisionPort {
    Optional<PostContentRevision> find(long postId, long revision);

    void insert(PostContentRevision revision);
}
