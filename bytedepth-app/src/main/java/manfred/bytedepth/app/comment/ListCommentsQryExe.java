package manfred.bytedepth.app.comment;

import lombok.RequiredArgsConstructor;
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
                .map(c -> { CommentDTO dto = new CommentDTO(); dto.setId(c.getId()); dto.setPostId(c.getPostId()); dto.setAuthorName(c.getAuthorName()); dto.setContent(c.getContent()); dto.setStatus(c.getStatus().name()); dto.setCreatedAt(c.getCreatedAt()); return dto; })
                .collect(Collectors.toList());
    }

    public List<CommentDTO> findPending() {
        return commentRepository.findPending().stream()
                .map(c -> { CommentDTO dto = new CommentDTO(); dto.setId(c.getId()); dto.setPostId(c.getPostId()); dto.setAuthorName(c.getAuthorName()); dto.setContent(c.getContent()); dto.setStatus(c.getStatus().name()); dto.setCreatedAt(c.getCreatedAt()); return dto; })
                .collect(Collectors.toList());
    }
}
