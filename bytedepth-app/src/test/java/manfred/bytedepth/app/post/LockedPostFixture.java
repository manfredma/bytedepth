package manfred.bytedepth.app.post;

import java.util.function.Function;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;

/** Unit fixture only; real SQL locking is exercised by infrastructure and staging tests. */
public final class LockedPostFixture {
    private LockedPostFixture() {}

    public static PostWriteTransaction from(PostRepository posts) {
        return new PostWriteTransaction() {
            public <T> T withLockedPost(Long id, Function<Post, T> action) {
                return action.apply(posts.findById(id).orElseThrow(() -> new DomainException("文章不存在：" + id)));
            }
        };
    }
}
