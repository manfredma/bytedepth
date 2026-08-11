package manfred.bytedepth.domain.comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    Comment save(Comment comment);
    Optional<Comment> findById(Long id);
    List<Comment> findApprovedByPostId(Long postId);
    /** 管理员用：返回最近评论（所有状态），按时间倒序 */
    List<Comment> findAll(int page, int size);

    /** 管理员用：按过滤条件分页查询评论（authorName 模糊、postId 精确），按时间倒序 */
    List<Comment> findAll(int page, int size, String authorName, Long postId);

    /** 管理员用：按过滤条件统计评论总数 */
    long countFiltered(String authorName, Long postId);
}
