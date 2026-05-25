package manfred.bytedepth.app.post.query;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListAllPostsQryExe {

    private final PostRepository postRepository;

    public List<PostDTO> execute(int page, int size) {
        List<Post> posts = postRepository.findAll(page, size);
        return posts.stream().map(this::toDTO).collect(Collectors.toList());
    }

    private PostDTO toDTO(Post post) {
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
