package manfred.bytedepth.domain.post;

import java.util.List;
import java.util.Optional;

public interface PostRepository {
    Post save(Post post);
    Optional<Post> findById(Long id);
    List<Post> findPublished(int page, int size);
    List<HotPost> findPublishedByHotness(int page, int size);
    List<Post> findLatestPublishedExcluding(List<Long> excludedIds, int limit);
    long countPublished();
    List<Post> findPublishedByTag(String tagSlug, int page, int size);
    long countPublishedByTag(String tagSlug);
    List<Post> findPublishedByCategory(Long categoryId, int page, int size);
    long countPublishedByCategory(Long categoryId);
    List<Post> findPage(int page, int size);
    long countAll();
    Optional<Post> findPrevPublished(Long id);
    Optional<Post> findNextPublished(Long id);
    void setPostSeries(Long postId, Long seriesId, Integer seriesOrder);
    /** 清除文章的专栏绑定（series_id、series_order 置 null） */
    void clearPostSeries(Long postId);

    List<Post> findPublishedByAuthorId(Long authorId, int page, int size);
    long countPublishedByAuthorId(Long authorId);

    Optional<Post> findBySlug(String slug);

    /** 直接更新 slug 字段（不触及其他字段）。调用方需保证格式合法且无冲突。 */
    void updateSlug(Long id, String slug);

    /** 查询所有已发布文章（不分页），用于生成 sitemap。 */
    List<Post> findAllPublished();
}
