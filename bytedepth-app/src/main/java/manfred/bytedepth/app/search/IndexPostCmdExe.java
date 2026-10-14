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
    private static final int BATCH_SIZE = 100;

    private final PostRepository postRepository;
    private final TagRepository tagRepository;
    private final CategoryRepository categoryRepository;
    private final SeriesRepository seriesRepository;
    private final PostSearchPort postSearchPort;

    // Serialize projection reads and writes in the current single application instance:
    // a slow published write must finish before a later deletion can remove its document.
    public synchronized void execute(Long postId) {
        PostSearchDoc doc = document(postId);
        if (doc == null) postSearchPort.delete(postId);
        else postSearchPort.index(doc);
    }

    public synchronized void executeBatch(List<Long> postIds) {
        for (int offset = 0; offset < postIds.size(); offset += BATCH_SIZE) {
            synchronizeBatch(postIds.subList(offset, Math.min(offset + BATCH_SIZE, postIds.size())));
        }
    }

    private void synchronizeBatch(List<Long> postIds) {
        List<PostSearchDoc> documents = new java.util.ArrayList<>();
        List<Long> deletedIds = new java.util.ArrayList<>();
        for (Long id : postIds) {
            PostSearchDoc doc = document(id);
            if (doc == null) deletedIds.add(id);
            else documents.add(doc);
        }
        if (!documents.isEmpty()) postSearchPort.indexAll(documents);
        if (!deletedIds.isEmpty()) postSearchPort.deleteAll(deletedIds);
    }

    private PostSearchDoc document(Long postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null || post.getStatus() != PostStatus.PUBLISHED) return null;

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

        return PostSearchDoc.builder()
                .id(postId)
                .slug(post.getSlug())
                .title(post.getTitle())
                .content(MarkdownTextExtractor.plainText(post.getContent()))
                .categoryName(categoryName)
                .categorySlug(categorySlug)
                .tags(tags)
                .seriesName(seriesName)
                .build();
    }
}
