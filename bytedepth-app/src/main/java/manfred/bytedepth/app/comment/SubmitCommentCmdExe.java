package manfred.bytedepth.app.comment;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubmitCommentCmdExe {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    /**
     * 提交评论。username 来自 SecurityContext，内部解析为 authorId。
     * authorName 取用户名快照，用于评论显示。
     */
    public void execute(Long postId, String username, String content) {
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new DomainException("用户不存在：" + username));
        Comment comment = Comment.create(postId, user.getId(), user.getUsername(), content);
        commentRepository.save(comment);
    }
}
