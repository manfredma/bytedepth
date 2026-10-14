package manfred.bytedepth.app.post.command;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import manfred.bytedepth.app.search.IndexPostCmdExe;
import manfred.bytedepth.app.series.DeleteSeriesCmdExe;
import manfred.bytedepth.app.tag.DeleteTagCmdExe;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.search.PostSearchPort;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import org.junit.jupiter.api.Test;

class MutationSearchSyncTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final TagRepository tags = mock(TagRepository.class);
    private final SeriesRepository series = mock(SeriesRepository.class);
    private final PostSearchPort search = mock(PostSearchPort.class);
    private final IndexPostCmdExe index =
            new IndexPostCmdExe(posts, tags, mock(CategoryRepository.class), series, search);

    private Post published(Long id) {
        Post post = Post.reconstruct(
                id,
                "Title",
                "Content",
                PostStatus.PUBLISHED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now());
        when(posts.findById(id)).thenReturn(Optional.of(post));
        return post;
    }

    @Test
    void sharedTagRename_refreshesEveryPublishedArticleAfterAssociationWrite() {
        Post first = published(1L);
        Post second = published(2L);
        Tag old = Tag.reconstruct(3L, "Old", "java");
        Tag renamed = Tag.reconstruct(3L, "New", "java");
        when(tags.findBySlug("java")).thenReturn(Optional.of(old));
        when(tags.save(any())).thenReturn(renamed);
        when(posts.findPublishedByTag("java", 1, 100)).thenReturn(List.of(first, second));
        when(tags.findByPostId(anyLong())).thenReturn(List.of(renamed));

        new SetPostTagsCmdExe(tags, posts, index).execute(1L, List.of("java:New"));

        var order = inOrder(tags, search);
        order.verify(tags).savePostTags(1L, List.of(3L));
        order.verify(search)
                .indexAll(argThat(docs -> docs.size() == 2
                        && docs.get(0).getId().equals(1L)
                        && docs.get(1).getId().equals(2L)
                        && docs.stream().allMatch(doc -> doc.getTags().equals(List.of("New")))));
        verify(posts).findPublishedByTag("java", 2, 100);
    }

    @Test
    void clearingTags_refreshesTheTargetWithEmptyTags() {
        published(1L);
        new SetPostTagsCmdExe(tags, posts, index).execute(1L, List.of());
        var order = inOrder(tags, search);
        order.verify(tags).savePostTags(1L, List.of());
        order.verify(search)
                .indexAll(argThat(
                        docs -> docs.size() == 1 && docs.get(0).getTags().isEmpty()));
    }

    @Test
    void tagDeletion_snapshotsAssociatedArticlesBeforeDeleting() {
        Post post = published(1L);
        when(tags.findById(3L)).thenReturn(Optional.of(Tag.reconstruct(3L, "Java", "java")));
        when(posts.findPublishedByTag("java", 1, 100)).thenReturn(List.of(post));
        new DeleteTagCmdExe(tags, posts, index).execute(3L);
        var order = inOrder(posts, tags, search);
        order.verify(posts).findPublishedByTag("java", 1, 100);
        order.verify(posts).findPublishedByTag("java", 2, 100);
        order.verify(tags).deleteWithPostAssociations(3L);
        order.verify(search)
                .indexAll(argThat(docs -> docs.size() == 1
                        && docs.get(0).getId().equals(1L)
                        && docs.get(0).getTags().isEmpty()));
    }

    @Test
    void missingTag_stillPerformsIdempotentDeletion() {
        new DeleteTagCmdExe(tags, posts, index).execute(3L);
        verify(tags).deleteWithPostAssociations(3L);
        verifyNoInteractions(search);
    }

    @Test
    void seriesDeletion_snapshotsArticlesBeforeClearingAssociations() {
        published(1L);
        when(series.findAllPostsBySeries(3L)).thenReturn(List.of(new SeriesPostItem(1L, "Title", 1)));
        new DeleteSeriesCmdExe(series, index).execute(3L);
        var order = inOrder(series, search);
        order.verify(series).findAllPostsBySeries(3L);
        order.verify(series).deleteWithPosts(3L);
        order.verify(search)
                .indexAll(argThat(docs -> docs.size() == 1
                        && docs.get(0).getId().equals(1L)
                        && docs.get(0).getSeriesName() == null));
    }

    @Test
    void emptySeriesDeletionDoesNotSubmitSearchTasks() {
        new DeleteSeriesCmdExe(series, index).execute(3L);
        verifyNoInteractions(search);
    }

    @Test
    void slugMutation_isSavedBeforeRefreshingTheSearchDocument() {
        Post post = Post.reconstruct(
                1L,
                "new-slug",
                "Title",
                "Content",
                PostStatus.PUBLISHED,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                null,
                false);
        when(posts.findById(1L)).thenReturn(Optional.of(post));
        new UpdatePostSlugCmdExe(posts, index).execute(1L, "new-slug");
        var order = inOrder(posts, search);
        order.verify(posts).updateSlug(1L, "new-slug");
        order.verify(search).index(argThat(doc -> "new-slug".equals(doc.getSlug())));
    }

    @Test
    void indexingFailure_isReportedAfterDatabaseMutation() {
        published(1L);
        doThrow(new IllegalStateException("search unavailable")).when(search).index(any());
        assertThrows(IllegalStateException.class, () -> new UpdatePostSlugCmdExe(posts, index).execute(1L, "new-slug"));
        verify(posts).updateSlug(1L, "new-slug");
    }

    @Test
    void failedDatabaseMutation_doesNotWriteSearch() {
        doThrow(new IllegalStateException("database unavailable")).when(posts).updateSlug(1L, "new-slug");
        assertThrows(IllegalStateException.class, () -> new UpdatePostSlugCmdExe(posts, index).execute(1L, "new-slug"));
        verifyNoInteractions(search);
    }

    @Test
    void tagRename_snapshotsFullAndTerminalPagesBeforeSaving() {
        List<Post> page = java.util.stream.LongStream.rangeClosed(1, 100)
                .mapToObj(this::published)
                .toList();
        Post last = published(101L);
        when(tags.findBySlug("java")).thenReturn(Optional.of(Tag.reconstruct(3L, "Old", "java")));
        when(tags.save(any())).thenReturn(Tag.reconstruct(3L, "New", "java"));
        when(posts.findPublishedByTag("java", 1, 100)).thenReturn(page);
        when(posts.findPublishedByTag("java", 2, 100)).thenReturn(List.of(last));
        IndexPostCmdExe sync = mock(IndexPostCmdExe.class);
        new SetPostTagsCmdExe(tags, posts, sync).execute(1L, List.of("java:New"));
        var order = inOrder(posts, tags, sync);
        order.verify(posts).findPublishedByTag("java", 1, 100);
        order.verify(posts).findPublishedByTag("java", 2, 100);
        order.verify(posts).findPublishedByTag("java", 3, 100);
        order.verify(tags).save(any());
        order.verify(tags).savePostTags(1L, List.of(3L));
        order.verify(sync).executeBatch(argThat(ids -> ids.size() == 101 && ids.contains(101L)));
    }

    @Test
    void tagDeletion_snapshotsFullAndTerminalPagesBeforeDeleting() {
        List<Post> page = java.util.stream.LongStream.rangeClosed(1, 100)
                .mapToObj(this::published)
                .toList();
        Post last = published(101L);
        when(tags.findById(3L)).thenReturn(Optional.of(Tag.reconstruct(3L, "Java", "java")));
        when(posts.findPublishedByTag("java", 1, 100)).thenReturn(page);
        when(posts.findPublishedByTag("java", 2, 100)).thenReturn(List.of(last));
        IndexPostCmdExe sync = mock(IndexPostCmdExe.class);
        new DeleteTagCmdExe(tags, posts, sync).execute(3L);
        var order = inOrder(posts, tags, sync);
        order.verify(posts).findPublishedByTag("java", 1, 100);
        order.verify(posts).findPublishedByTag("java", 2, 100);
        order.verify(posts).findPublishedByTag("java", 3, 100);
        order.verify(tags).deleteWithPostAssociations(3L);
        order.verify(sync).executeBatch(argThat(ids -> ids.size() == 101 && ids.contains(101L)));
    }
}
