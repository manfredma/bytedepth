package manfred.bytedepth.domain.comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    Comment save(Comment comment);
    Optional<Comment> findById(Long id);
    List<Comment> findApprovedByPostId(Long postId);
    /** 管理员用：返回最近评论（所有状态），按时间倒序 */
    List<Comment> findAll(int page, int size);
}
