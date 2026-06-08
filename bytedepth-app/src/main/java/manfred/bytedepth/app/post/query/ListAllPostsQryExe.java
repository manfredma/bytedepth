package manfred.bytedepth.app.post.query;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListAllPostsQryExe {

    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;

    public record PageResult(List<PostDTO> posts, long total) {}

    public PageResult execute(int page, int size) {
        List<PostDTO> posts = postRepository.findPage(page, size)
                .stream().map(this::toDTO).collect(Collectors.toList());
        long total = postRepository.countAll();
        return new PageResult(posts, total);
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
        dto.setSeriesId(post.getSeriesId());
        if (post.getSeriesId() != null) {
            seriesRepository.findById(post.getSeriesId()).ifPresent(s -> {
                dto.setSeriesName(s.getName());
                dto.setSeriesSlug(s.getSlug());
            });
        }
        return dto;
    }
}
