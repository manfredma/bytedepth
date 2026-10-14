package manfred.bytedepth.app.search;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.*;
import manfred.bytedepth.app.post.command.DeletePostCmdExe;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.*;
import manfred.bytedepth.domain.search.*;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.tag.TagRepository;
import org.junit.jupiter.api.Test;

class SearchLifecycleTest {
    private final Map<Long, Post> records = new java.util.concurrent.ConcurrentHashMap<>();
    private final PostRepository posts = mock(PostRepository.class);
    private final MemorySearch search = new MemorySearch();
    private final IndexPostCmdExe index = new IndexPostCmdExe(
            posts, mock(TagRepository.class), mock(CategoryRepository.class), mock(SeriesRepository.class), search);

    SearchLifecycleTest() {
        when(posts.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(records.get(i.getArgument(0))));
        when(posts.save(any())).thenAnswer(i -> {
            Post p = i.getArgument(0);
            records.put(p.getId(), p);
            return p;
        });
    }

    private Post add(long id, PostStatus status) {
        Post p = Post.reconstruct(
                id,
                "old-slug",
                "Java features",
                "body",
                status,
                LocalDateTime.now(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                null,
                1L,
                false);
        records.put(id, p);
        return p;
    }

    @Test
    void deletingPublishedArticleRemovesItsSearchHit() {
        add(1, PostStatus.PUBLISHED);
        index.execute(1L);
        assertEquals(1, search.search("Java", 1, 10).getTotalHits());
        new DeletePostCmdExe(posts, index).execute(1L);
        assertEquals(PostStatus.DELETED, records.get(1L).getStatus());
        assertTrue(search.search("Java", 1, 10).getHits().isEmpty());
    }

    @Test
    void indexingNonPublishedOrMissingArticlePurgesAnExistingDocument() {
        for (PostStatus status : List.of(PostStatus.DRAFT, PostStatus.DELETED)) {
            add(2, status);
            search.index(PostSearchDoc.builder().id(2L).title("stale").build());
            index.execute(2L);
            assertFalse(search.documents.containsKey(2L), status.name());
        }
        records.clear();
        search.index(PostSearchDoc.builder().id(3L).title("orphan").build());
        index.execute(3L);
        assertFalse(search.documents.containsKey(3L));
    }

    @Test
    void fullReindexRemovesDeletedMissingAndDraftDocumentsAndRefreshesPublished() {
        Post published = add(1, PostStatus.PUBLISHED);
        add(2, PostStatus.DRAFT);
        add(3, PostStatus.DELETED);
        for (long id = 1; id <= 4; id++) {
            search.index(
                    PostSearchDoc.builder().id(id).title("old Java").slug("old").build());
        }
        when(posts.findPage(1, 50)).thenReturn(List.of(published, records.get(2L)));
        when(posts.findAllPublished()).thenReturn(List.of(published));
        assertEquals(1, new ReindexAllPostsCmdExe(posts, index, search).execute());
        assertEquals(Set.of(1L), search.documents.keySet());
        assertEquals("old-slug", search.documents.get(1L).getSlug());
    }

    @Test
    void searchOmitsUnavailableArticlesAndUsesDatabaseSlugWithHighlights() {
        Post published = add(1, PostStatus.PUBLISHED);
        add(2, PostStatus.DELETED);
        add(3, PostStatus.DRAFT);
        search.index(PostSearchDoc.builder()
                .id(1L)
                .title("<em>Java</em>")
                .slug("obsolete")
                .build());
        for (long id = 2; id <= 4; id++) {
            search.index(PostSearchDoc.builder()
                    .id(id)
                    .title("Java")
                    .slug("obsolete")
                    .build());
        }
        SearchResult result = new SearchPostsQryExe(search, posts).execute("Java", 1);
        assertEquals(1, result.getHits().size());
        assertEquals(published.getSlug(), result.getHits().get(0).getSlug());
        assertEquals("<em>Java</em>", result.getHits().get(0).getTitle());
    }

    @Test
    void inFlightPublishedWriteCannotResurrectArticleAfterConcurrentDeletion() throws Exception {
        add(1, PostStatus.PUBLISHED);
        var writeStarted = new java.util.concurrent.CountDownLatch(1);
        var deletionApplied = new java.util.concurrent.CountDownLatch(1);
        MemorySearch delayed = new MemorySearch() {
            @Override
            public void index(PostSearchDoc doc) {
                writeStarted.countDown();
                try {
                    deletionApplied.await(1, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                super.index(doc);
            }

            @Override
            public void delete(Long id) {
                super.delete(id);
                deletionApplied.countDown();
            }
        };
        IndexPostCmdExe serialIndex = new IndexPostCmdExe(
                posts,
                mock(TagRepository.class),
                mock(CategoryRepository.class),
                mock(SeriesRepository.class),
                delayed);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var writing = executor.submit(() -> serialIndex.execute(1L));
            assertTrue(writeStarted.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var deleting = executor.submit(() -> new DeletePostCmdExe(posts, serialIndex).execute(1L));
            writing.get(5, java.util.concurrent.TimeUnit.SECONDS);
            deleting.get(5, java.util.concurrent.TimeUnit.SECONDS);
        }
        assertFalse(delayed.documents.containsKey(1L));
    }

    static class MemorySearch implements PostSearchPort {
        final Map<Long, PostSearchDoc> documents = new LinkedHashMap<>();

        public void index(PostSearchDoc doc) {
            documents.put(doc.getId(), doc);
        }

        public void delete(Long id) {
            documents.remove(id);
        }

        public List<Long> findIndexedIds() {
            return new ArrayList<>(documents.keySet());
        }

        public SearchResult search(String query, int page, int size) {
            List<PostSearchDoc> matches = documents.values().stream()
                    .filter(d -> d.getTitle().contains(query))
                    .toList();
            return new SearchResult(
                    matches.stream().skip((long) (page - 1) * size).limit(size).toList(), matches.size(), page, size);
        }
    }
}
