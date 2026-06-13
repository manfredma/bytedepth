package manfred.bytedepth.app.post.query;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.tag.TagRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GetPostQryExe {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;

    public PostDTO execute(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new java.util.NoSuchElementException("博文不存在：" + postId));
        return buildDTO(post);
    }

    public PostDTO executeBySlug(String slug) {
        Post post = postRepository.findBySlug(slug)
                .orElseThrow(() -> new java.util.NoSuchElementException("博文不存在：" + slug));
        return buildDTO(post);
    }

    private PostDTO buildDTO(Post post) {
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        dto.setSlug(post.getSlug());
        dto.setAuthorId(post.getAuthorId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setStatus(post.getStatus().name());
        dto.setPublishedAt(post.getPublishedAt());
        dto.setUpdatedAt(post.getUpdatedAt());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setCategoryId(post.getCategoryId());
        List<String> tagSlugs = tagRepository.findByPostId(post.getId()).stream()
                .map(t -> t.getSlug())
                .collect(Collectors.toList());
        dto.setTagSlugs(tagSlugs);
        return dto;
    }
}
