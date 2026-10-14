package manfred.bytedepth.app.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import manfred.bytedepth.domain.category.Category;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.search.PostSearchDoc;
import manfred.bytedepth.domain.search.PostSearchPort;
import manfred.bytedepth.domain.search.SearchResult;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SearchCommandsTest {

    private final PostRepository posts = mock(PostRepository.class);
    private final TagRepository tags = mock(TagRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final SeriesRepository series = mock(SeriesRepository.class);
    private final PostSearchPort search = mock(PostSearchPort.class);
    private final IndexPostCmdExe index = new IndexPostCmdExe(posts, tags, categories, series, search);

    @Test
    void index_skipsMissingPostAndIncludesResolvedMetadata() {
        when(posts.findById(1L)).thenReturn(Optional.empty());
        index.execute(1L);
        verify(search).delete(1L);

        Post post = Post.reconstruct(
                2L,
                "slug",
                "title",
                "body prose",
                PostStatus.PUBLISHED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                3L,
                null,
                false);
        post.assignSeries(4L, 1);
        when(posts.findById(2L)).thenReturn(Optional.of(post));
        when(tags.findByPostId(2L)).thenReturn(List.of(Tag.reconstruct(1L, "Java", "java")));
        when(categories.findById(3L)).thenReturn(Optional.of(Category.reconstruct(3L, "Backend", "backend", null)));
        when(series.findById(4L)).thenReturn(Optional.of(Series.reconstruct(4L, "Column", "column", null, 1L)));

        index.execute(2L);

        ArgumentCaptor<PostSearchDoc> document = ArgumentCaptor.forClass(PostSearchDoc.class);
        verify(search).index(document.capture());
        assertEquals("Backend", document.getValue().getCategoryName());
        assertEquals(List.of("Java"), document.getValue().getTags());
        assertEquals("Column", document.getValue().getSeriesName());
    }

    @Test
    void index_skipsMissingCategoryAndMissingSeriesAndNullAssociations() {
        // categoryId 非空但分类不存在；seriesId 为 null；无标签
        Post post = Post.reconstruct(
                3L,
                "s",
                "t",
                "正文",
                PostStatus.PUBLISHED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                8L,
                4L,
                false);
        when(posts.findById(3L)).thenReturn(Optional.of(post));
        when(tags.findByPostId(3L)).thenReturn(List.of());
        when(categories.findById(8L)).thenReturn(Optional.empty());

        index.execute(3L);

        ArgumentCaptor<PostSearchDoc> document = ArgumentCaptor.forClass(PostSearchDoc.class);
        verify(search).index(document.capture());
        assertNull(document.getValue().getCategoryName());
        assertNull(document.getValue().getCategorySlug());
        assertNull(document.getValue().getSeriesName());
        assertTrue(document.getValue().getTags().isEmpty());
        assertEquals("正文", document.getValue().getContent());
    }

    @Test
    void index_resolvesCategoryButMissingSeries() {
        Post post = Post.reconstruct(
                4L,
                "s2",
                "t2",
                "c2",
                PostStatus.PUBLISHED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                3L,
                4L,
                false);
        post.assignSeries(5L, 1);
        when(posts.findById(4L)).thenReturn(Optional.of(post));
        when(tags.findByPostId(4L)).thenReturn(List.of());
        when(categories.findById(3L)).thenReturn(Optional.of(Category.reconstruct(3L, "Backend", "backend", null)));
        when(series.findById(5L)).thenReturn(Optional.empty());

        index.execute(4L);

        ArgumentCaptor<PostSearchDoc> document = ArgumentCaptor.forClass(PostSearchDoc.class);
        verify(search).index(document.capture());
        assertEquals("Backend", document.getValue().getCategoryName());
        assertEquals("backend", document.getValue().getCategorySlug());
        assertNull(document.getValue().getSeriesName());
    }

    @Test
    void index_withNullCategoryId_skipsCategoryResolution() {
        // categoryId 为 null：跳过整个分类解析块（覆盖 if 的 false 分支）
        Post post = Post.reconstruct(
                6L,
                "s3",
                "t3",
                "c3",
                PostStatus.PUBLISHED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                4L,
                false);
        when(posts.findById(6L)).thenReturn(Optional.of(post));
        when(tags.findByPostId(6L)).thenReturn(List.of());

        index.execute(6L);

        ArgumentCaptor<PostSearchDoc> document = ArgumentCaptor.forClass(PostSearchDoc.class);
        verify(search).index(document.capture());
        assertNull(document.getValue().getCategoryName());
        assertNull(document.getValue().getCategorySlug());
        assertNull(document.getValue().getSeriesName());
        verifyNoInteractions(categories);
    }

    @Test
    void reindexRefreshesPublishedSourceSnapshot() {
        Post published = Post.reconstruct(
                1L,
                "p",
                "t",
                "c",
                PostStatus.PUBLISHED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                false);
        Post draft = Post.reconstruct(
                2L, "d", "t", "c", PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(), null, null, false);
        when(posts.findAllPublished()).thenReturn(List.of(published));

        IndexPostCmdExe indexer = mock(IndexPostCmdExe.class);
        assertEquals(1, new ReindexAllPostsCmdExe(posts, indexer, search).execute());
        verify(indexer).executeBatch(List.of(1L));
    }

    @Test
    void reindexHandlesEmptyAndLargePublishedSnapshots() {
        when(posts.findAllPublished()).thenReturn(List.of());
        IndexPostCmdExe indexer = mock(IndexPostCmdExe.class);
        assertEquals(0, new ReindexAllPostsCmdExe(posts, indexer, search).execute());
        verifyNoInteractions(indexer);

        Post published = Post.reconstruct(
                1L,
                "p",
                "t",
                "c",
                PostStatus.PUBLISHED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                false);
        // All snapshot IDs are synchronized, independently of legacy page boundaries.
        when(posts.findAllPublished())
                .thenReturn(java.util.stream.IntStream.range(0, 50)
                        .mapToObj(i -> published)
                        .toList());

        assertEquals(50, new ReindexAllPostsCmdExe(posts, indexer, search).execute());
    }

    @Test
    void reindexDoesNotSkipPublishedArticleWhenEarlierRowIsRemovedDuringScan() {
        var source = new java.util.ArrayList<Post>();
        for (long id = 1; id <= 51; id++) {
            source.add(Post.reconstruct(
                    id,
                    "p" + id,
                    "title",
                    "body",
                    PostStatus.PUBLISHED,
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    LocalDateTime.now(),
                    null,
                    null,
                    false));
        }
        when(posts.findAllPublished()).thenAnswer(i -> List.copyOf(source));

        IndexPostCmdExe indexer = mock(IndexPostCmdExe.class);
        doAnswer(i -> {
                    source.remove(0);
                    return null;
                })
                .when(indexer)
                .executeBatch(anyList());
        new ReindexAllPostsCmdExe(posts, indexer, search).execute();
        verify(indexer).executeBatch(argThat(ids -> ids.contains(51L)));
    }

    @Test
    void search_trimsMeaningfulQueriesAndReturnsEmptyForBlankInput() {
        SearchPostsQryExe query = new SearchPostsQryExe(search, posts);
        SearchResult expected = new SearchResult(List.of(), 0, 2, 10);
        when(search.search("java", 2, 10)).thenReturn(expected);
        assertEquals(0, query.execute("  ", 1).getTotalHits());
        assertEquals(0, query.execute(null, 1).getTotalHits());
        assertEquals(0, query.execute(" java ", 2).getTotalHits());
    }
}
