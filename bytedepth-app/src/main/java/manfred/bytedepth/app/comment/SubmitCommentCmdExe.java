package manfred.bytedepth.app.comment;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubmitCommentCmdExe {

    private final CommentRepository commentRepository;

    public void execute(Long postId, String authorName, String authorEmail, String content) {
        Comment comment = Comment.create(postId, authorName, authorEmail, content);
        commentRepository.save(comment);
    }
}
