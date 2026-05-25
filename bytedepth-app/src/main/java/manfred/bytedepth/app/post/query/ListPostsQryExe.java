package manfred.bytedepth.app.post.query;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.category.Category;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListPostsQryExe {

    private final PostRepository postRepository;
    private final CategoryRepository categoryRepository;

    public List<PostDTO> execute(int page, int size) {
        List<Post> posts = postRepository.findPublished(page, size);
        return posts.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<PostDTO> executeByTag(String tagSlug, int page, int size) {
        return postRepository.findPublishedByTag(tagSlug, page, size)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<PostDTO> executeByCategory(String categorySlug, int page, int size) {
        return categoryRepository.findBySlug(categorySlug)
                .map(cat -> postRepository.findPublishedByCategory(cat.getId(), page, size)
                        .stream().map(this::toDTO).collect(Collectors.toList()))
                .orElseGet(List::of);
    }

    private PostDTO toDTO(Post post) {
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        dto.setTitle(post.getTitle());
        dto.setContent(post.getContent());
        dto.setStatus(post.getStatus().name());
        dto.setPublishedAt(post.getPublishedAt());
        dto.setCreatedAt(post.getCreatedAt());
        dto.setCategoryId(post.getCategoryId());
        if (post.getCategoryId() != null) {
            categoryRepository.findById(post.getCategoryId()).ifPresent(cat -> {
                dto.setCategoryName(cat.getName());
                dto.setCategorySlug(cat.getSlug());
            });
        }
        return dto;
    }
}
