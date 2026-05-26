package manfred.bytedepth.app.search;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.tag.TagRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class IndexPostCmdExe {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final SeriesRepository seriesRepository;
    private final PostSearchPort postSearchPort;

    public void execute(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) return;

        List<String> tags = tagRepository.findByPostId(postId).stream()
                .map(t -> t.getName())
                .toList();

        String categoryName = null;
        String categorySlug = null;
        if (post.getCategoryId() != null) {
            var cat = categoryRepository.findById(post.getCategoryId()).orElse(null);
            if (cat != null) {
                categoryName = cat.getName();
                categorySlug = cat.getSlug();
            }
        }

        String seriesName = null;
        if (post.getSeriesId() != null) {
            seriesName = seriesRepository.findById(post.getSeriesId())
                    .map(s -> s.getName())
                    .orElse(null);
        }

        PostSearchDoc doc = PostSearchDoc.builder()
                .id(postId)
                .title(post.getTitle())
                .content(post.getContent())
                .categoryName(categoryName)
                .categorySlug(categorySlug)
                .tags(tags)
                .seriesName(seriesName)
                .build();

        postSearchPort.index(doc);
    }
}
