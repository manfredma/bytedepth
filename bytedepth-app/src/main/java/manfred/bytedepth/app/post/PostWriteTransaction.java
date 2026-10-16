package manfred.bytedepth.app.post;

import java.util.function.Function;
import manfred.bytedepth.domain.post.Post;

public interface PostWriteTransaction {
    <T> T withLockedPost(Long postId, Function<Post, T> action);
}
