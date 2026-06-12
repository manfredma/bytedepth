package manfred.bytedepth.app.comment;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListCommentsQryExe {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public List<CommentDTO> findApprovedByPostId(Long postId) {
        return commentRepository.findApprovedByPostId(postId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /** 管理员用：返回最近评论列表，附带文章 slug（用于后台跳转链接） */
    public List<CommentDTO> findAll(int page, int size) {
        List<Comment> comments = commentRepository.findAll(page, size);

        // 批量查询 slug，避免 N+1
        Set<Long> postIds = comments.stream()
            .map(Comment::getPostId).collect(Collectors.toSet());
        Map<Long, String> slugMap = postIds.stream()
            .collect(Collectors.toMap(
                id -> id,
                id -> postRepository.findById(id).map(p -> p.getSlug()).orElse("")
            ));

        return comments.stream()
            .map(c -> {
                CommentDTO dto = toDTO(c);
                dto.setPostSlug(slugMap.getOrDefault(c.getPostId(), ""));
                return dto;
            })
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
