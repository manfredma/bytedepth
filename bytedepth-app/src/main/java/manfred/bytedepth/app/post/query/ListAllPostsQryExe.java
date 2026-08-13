package manfred.bytedepth.app.post.query;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.AuthorPostRepository;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListAllPostsQryExe {

    private final PostRepository postRepository;
    private final AuthorPostRepository authorPostRepository;
    private final SeriesRepository seriesRepository;

    public record PageResult(List<PostDTO> posts, long total) {}

    public PageResult execute(int page, int size) {
        List<PostDTO> posts = postRepository.findPage(page, size)
                .stream().map(this::toDTO).collect(Collectors.toList());
        long total = postRepository.countAll();
        return new PageResult(posts, total);
    }

    public PageResult executeByAuthor(Long authorId, int page, int size) {
        List<PostDTO> posts = authorPostRepository.findPageByAuthorId(authorId, page, size)
                .stream().map(this::toDTO).collect(Collectors.toList());
        long total = authorPostRepository.countByAuthorId(authorId);
        return new PageResult(posts, total);
    }

    /** 后台管理用：按过滤条件分页（title 模糊、status 精确、seriesId/categoryId 精确）。 */
    public PageResult execute(int page, int size, String title, String status, Long seriesId, Long categoryId) {
        List<PostDTO> posts = postRepository.findPage(page, size, title, status, seriesId, categoryId)
                .stream().map(this::toDTO).collect(Collectors.toList());
        long total = postRepository.countFiltered(title, status, seriesId, categoryId);
        return new PageResult(posts, total);
    }

    /** 个人工作台用：按过滤条件分页查询某位作者的文章。 */
    public PageResult executeByAuthor(Long authorId, int page, int size,
                                      String title, String status, Long seriesId, Long categoryId) {
        List<PostDTO> posts = authorPostRepository.findPageByAuthorId(authorId, page, size, title, status, seriesId, categoryId)
                .stream().map(this::toDTO).collect(Collectors.toList());
        long total = authorPostRepository.countByAuthorIdFiltered(authorId, title, status, seriesId, categoryId);
        return new PageResult(posts, total);
    }

    private PostDTO toDTO(Post post) {
        PostDTO dto = new PostDTO();
        dto.setId(post.getId());
        dto.setSlug(post.getSlug());
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
