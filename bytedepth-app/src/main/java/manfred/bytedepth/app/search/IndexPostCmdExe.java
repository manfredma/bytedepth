package manfred.bytedepth.app.search;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.MarkdownTextExtractor;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.tag.TagRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IndexPostCmdExe {

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final SeriesRepository seriesRepository;
    private final PostSearchPort postSearchPort;

    // Serialize projection reads and writes in the current single application instance:
    // a slow published write must finish before a later deletion can remove its document.
    public synchronized void execute(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        // Search is a projection: retrying synchronization must also remove stale documents.
        if (post == null || post.getStatus() != PostStatus.PUBLISHED) {
            postSearchPort.delete(postId);
            return;
        }

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
            seriesName = seriesRepository
                    .findById(post.getSeriesId())
                    .map(s -> s.getName())
                    .orElse(null);
        }

        PostSearchDoc doc = PostSearchDoc.builder()
                .id(postId)
                .slug(post.getSlug())
                .title(post.getTitle())
                .content(MarkdownTextExtractor.plainText(post.getContent()))
                .categoryName(categoryName)
                .categorySlug(categorySlug)
                .tags(tags)
                .seriesName(seriesName)
                .build();

        postSearchPort.index(doc);
    }
}
