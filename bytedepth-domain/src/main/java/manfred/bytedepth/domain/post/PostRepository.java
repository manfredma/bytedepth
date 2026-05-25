package manfred.bytedepth.domain.post;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    Post save(Post post);
    Optional<Post> findById(Long id);
    List<Post> findPublished(int page, int size);
    long countPublished();
    List<Post> findPublishedByTag(String tagSlug, int page, int size);
    long countPublishedByTag(String tagSlug);
    List<Post> findPublishedByCategory(Long categoryId, int page, int size);
    long countPublishedByCategory(Long categoryId);
    List<Post> findAll(int page, int size);
    long countAll();
}
