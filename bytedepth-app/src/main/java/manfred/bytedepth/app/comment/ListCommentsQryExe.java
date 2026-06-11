package manfred.bytedepth.app.comment;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListCommentsQryExe {

    private final CommentRepository commentRepository;

    public List<CommentDTO> findApprovedByPostId(Long postId) {
        return commentRepository.findApprovedByPostId(postId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /** 管理员用：返回最近评论列表 */
    public List<CommentDTO> findAll(int page, int size) {
        return commentRepository.findAll(page, size).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    private CommentDTO toDTO(Comment c) {
        CommentDTO dto = new CommentDTO();
        dto.setId(c.getId());
        dto.setPostId(c.getPostId());
        dto.setAuthorId(c.getAuthorId());
        dto.setAuthorName(c.getAuthorName());
        dto.setContent(c.getContent());
        dto.setStatus(c.getStatus().name());
        dto.setCreatedAt(c.getCreatedAt());
        return dto;
    }
}
