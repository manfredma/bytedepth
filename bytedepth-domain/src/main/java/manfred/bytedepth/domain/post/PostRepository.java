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
    List<Post> findPage(int page, int size);
    long countAll();
    java.util.Optional<Post> findPrevPublished(Long id);
    java.util.Optional<Post> findNextPublished(Long id);
    void setPostSeries(Long postId, Long seriesId, Integer seriesOrder);
    /** 清除文章的专栏绑定（series_id、series_order 置 null） */
    void clearPostSeries(Long postId);

    List<Post> findPublishedByAuthorId(Long authorId, int page, int size);
    long countPublishedByAuthorId(Long authorId);
}
