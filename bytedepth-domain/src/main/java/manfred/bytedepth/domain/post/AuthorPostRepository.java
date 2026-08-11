package manfred.bytedepth.domain.post;

import java.util.List;

/** Personal-workspace queries that must be constrained to one author. */
public interface AuthorPostRepository {
    List<Post> findPageByAuthorId(Long authorId, int page, int size);
    long countByAuthorId(Long authorId);

    /** 个人工作台用：按过滤条件分页查询某位作者的文章（排除已删除）。 */
    List<Post> findPageByAuthorId(Long authorId, int page, int size, String title, String status, Long seriesId, Long categoryId);

    /** 个人工作台用：按过滤条件统计某位作者的文章总数（排除已删除）。 */
    long countByAuthorIdFiltered(Long authorId, String title, String status, Long seriesId, Long categoryId);
}
