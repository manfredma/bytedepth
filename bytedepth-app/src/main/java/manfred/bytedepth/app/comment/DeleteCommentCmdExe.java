package manfred.bytedepth.app.comment;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.CommentRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteCommentCmdExe {

    private final CommentRepository commentRepository;

    public void execute(Long commentId) {
        commentRepository.deleteById(commentId);
    }
}
