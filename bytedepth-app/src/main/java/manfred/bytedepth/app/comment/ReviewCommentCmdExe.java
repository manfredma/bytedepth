package manfred.bytedepth.app.comment;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReviewCommentCmdExe {

    private final CommentRepository commentRepository;

    public void approve(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("评论不存在：" + commentId));
        comment.approve();
        commentRepository.save(comment);
    }

    public void reject(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new RuntimeException("评论不存在：" + commentId));
        comment.reject();
        commentRepository.save(comment);
    }
}
