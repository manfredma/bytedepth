package manfred.bytedepth.app.post.query;

import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.PostWriteTransaction;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PublicPostAccess {
    private final PostWriteTransaction transactions;
    private final PostRepository posts;

    public <T> T withPublishedPost(Long id, Function<Post, T> action) {
        if (id == null || id < 1) throw new IllegalArgumentException("Invalid post ID");
        // The lock port throws an untyped DomainException for absent rows. Posts are soft-deleted;
        // preflight absence here and decide public visibility again from the locked current row.
        posts.findById(id).orElseThrow(Unavailable::new);
        return transactions.withLockedPost(id, post -> {
            if (post.getStatus() != manfred.bytedepth.domain.post.PostStatus.PUBLISHED) throw new Unavailable();
            return action.apply(post);
        });
    }

    public ResolvedPost resolve(String slug) {
        var found = posts.findBySlug(slug).orElseThrow(Unavailable::new);
        return withPublishedPost(found.getId(), post -> {
            if (!java.util.Objects.equals(slug, post.getSlug())) throw new Unavailable();
            return new ResolvedPost(post.getId().toString(), post.getSlug());
        });
    }

    public static final class Unavailable extends java.util.NoSuchElementException {}

    public record ResolvedPost(String postId, String slug) {}
}
