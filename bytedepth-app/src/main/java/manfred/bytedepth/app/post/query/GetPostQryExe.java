package manfred.bytedepth.app.post.query;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetPostQryExe {

    private final PostRepository postRepository;

    public PostDTO execute(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("博文不存在：" + postId));
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setStatus(post.getStatus().name());
        dto.setPublishedAt(post.getPublishedAt());
        dto.setCreatedAt(post.getCreatedAt());
        return dto;
    }
}
