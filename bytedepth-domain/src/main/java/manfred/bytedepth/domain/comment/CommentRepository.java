package manfred.bytedepth.domain.comment;

import java.util.List;
import java.util.Optional;

public interface CommentRepository {
    Comment save(Comment comment);
    Optional<Comment> findById(Long id);
    List<Comment> findApprovedByPostId(Long postId);
    List<Comment> findPending();
}
