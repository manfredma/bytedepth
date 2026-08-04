package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostRepositoryImplTest {

    private final PostMapper postMapper = Mockito.mock(PostMapper.class);
    private final PostRepositoryImpl repository = new PostRepositoryImpl(postMapper);

    // ---- save ----

    @Test
    void save_insertsNewPostWhenIdIsNull() {
        when(postMapper.insert(any())).thenAnswer(invocation -> {
            invocation.<PostDO>getArgument(0).setId(1L);
            return 1;
        });

        var post = manfred.bytedepth.domain.post.Post.create("Title", "Content", 7L, "slug");
        var saved = repository.save(post);

        assertEquals(1L, saved.getId());
        assertEquals("slug", saved.getSlug());
        assertEquals("Title", saved.getTitle());
        assertEquals("Content", saved.getContent());
        assertEquals(7L, saved.getAuthorId());
        assertFalse(saved.getFeatured());
        verify(postMapper).insert(any());
    }

    @Test
    void save_updatesExistingPostWhenIdNotNull() {
        var post = manfred.bytedepth.domain.post.Post.reconstruct(
            5L, "slug", "Updated", "body",
            manfred.bytedepth.domain.post.PostStatus.PUBLISHED,
            LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(),
            2L, 7L, true);

        var saved = repository.save(post);

        assertEquals(5L, saved.getId());
        assertEquals("Updated", saved.getTitle());
        assertTrue(saved.getFeatured());
        verify(postMapper).updateById(any());
    }

    @Test
    void save_featuredNullDefaultsToFalse() throws Exception {
        when(postMapper.insert(any())).thenAnswer(invocation -> {
            invocation.<PostDO>getArgument(0).setId(1L);
            return 1;
        });
        // Post factories always set featured to non-null; use reflection to force null
        // so we exercise the toDO ternary null-branch: post.getFeatured() != null ? ... : false
        var post = manfred.bytedepth.domain.post.Post.create("T", "C", null, "slug");
        Field featuredField = manfred.bytedepth.domain.post.Post.class.getDeclaredField("featured");
        featuredField.setAccessible(true);
        featuredField.set(post, null);

        var saved = repository.save(post);

        assertFalse(saved.getFeatured());
    }

    // ---- findById ----

    @Test
    void findById_returnsEntityWhenFound() {
        PostDO row = postRow(1L);
        when(postMapper.selectById(1L)).thenReturn(row);

        Optional<manfred.bytedepth.domain.post.Post> result = repository.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("slug", result.get().getSlug());
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        when(postMapper.selectById(99L)).thenReturn(null);

        assertTrue(repository.findById(99L).isEmpty());
    }

    // ---- findPublished ----

    @Test
    void findPublished_mapsRecordsToEntities() {
        Page<PostDO> page = new Page<>(1, 10);
        page.setRecords(List.of(postRow(1L), postRow(2L)));
        when(postMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var posts = repository.findPublished(1, 10);

        assertEquals(2, posts.size());
        assertEquals(1L, posts.get(0).getId());
        assertEquals(2L, posts.get(1).getId());
        verify(postMapper).selectPage(any(Page.class), any());
    }

    @Test
    void findPublished_emptyPageReturnsEmptyList() {
        Page<PostDO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(postMapper.selectPage(any(Page.class), any())).thenReturn(page);

        assertTrue(repository.findPublished(1, 10).isEmpty());
    }

    // ---- findPublishedByHotness ----

    @Test
    void findPublishedByHotness_mapsViewCountWhenNotNull() {
        HotPostDO hot = hotRow(1L, 42L);
        when(postMapper.findPublishedByHotness(0, 10)).thenReturn(List.of(hot));

        var result = repository.findPublishedByHotness(1, 10);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).post().getId());
        assertEquals(42L, result.get(0).viewCount());
    }

    @Test
    void findPublishedByHotness_viewCountNullDefaultsToZero() {
        HotPostDO hot = hotRow(1L, null);
        when(postMapper.findPublishedByHotness(10, 5)).thenReturn(List.of(hot));

        var result = repository.findPublishedByHotness(3, 5);

        assertEquals(0L, result.get(0).viewCount());
    }

    @Test
    void findPublishedByHotness_emptyListReturnsEmpty() {
        when(postMapper.findPublishedByHotness(0, 10)).thenReturn(List.of());

        assertTrue(repository.findPublishedByHotness(1, 10).isEmpty());
    }

    // ---- findLatestPublishedExcluding ----

    @Test
    void findLatestPublishedExcluding_mapsResults() {
        when(postMapper.findLatestPublishedExcluding(List.of(1L, 2L), 5))
            .thenReturn(List.of(postRow(3L)));

        var posts = repository.findLatestPublishedExcluding(List.of(1L, 2L), 5);

        assertEquals(1, posts.size());
        assertEquals(3L, posts.get(0).getId());
    }

    @Test
    void findLatestPublishedExcluding_emptyExcludedIdsReturnsResults() {
        when(postMapper.findLatestPublishedExcluding(List.of(), 3))
            .thenReturn(List.of(postRow(1L)));

        var posts = repository.findLatestPublishedExcluding(List.of(), 3);

        assertEquals(1, posts.size());
        assertEquals(1L, posts.get(0).getId());
    }

    @Test
    void findLatestPublishedExcluding_nullExcludedIdsReturnsResults() {
        when(postMapper.findLatestPublishedExcluding(null, 3))
            .thenReturn(List.of(postRow(1L)));

        var posts = repository.findLatestPublishedExcluding(null, 3);

        assertEquals(1, posts.size());
    }

    @Test
    void findLatestPublishedExcluding_emptyResultReturnsEmpty() {
        when(postMapper.findLatestPublishedExcluding(any(), anyInt()))
            .thenReturn(List.of());

        assertTrue(repository.findLatestPublishedExcluding(List.of(1L), 5).isEmpty());
    }

    // ---- countPublished ----

    @Test
    void countPublished_delegatesToMapper() {
        when(postMapper.selectCount(any())).thenReturn(42L);

        assertEquals(42L, repository.countPublished());
        verify(postMapper).selectCount(any());
    }

    // ---- findPublishedByTag ----

    @Test
    void findPublishedByTag_mapsResults() {
        when(postMapper.findPublishedByTagSlug("java", 0, 10))
            .thenReturn(List.of(postRow(1L)));

        var posts = repository.findPublishedByTag("java", 1, 10);

        assertEquals(1, posts.size());
        assertEquals(1L, posts.get(0).getId());
    }

    @Test
    void findPublishedByTag_emptyResultReturnsEmpty() {
        when(postMapper.findPublishedByTagSlug("java", 10, 10))
            .thenReturn(List.of());

        assertTrue(repository.findPublishedByTag("java", 2, 10).isEmpty());
    }

    // ---- countPublishedByTag ----

    @Test
    void countPublishedByTag_delegatesToMapper() {
        when(postMapper.countPublishedByTagSlug("java")).thenReturn(5L);

        assertEquals(5L, repository.countPublishedByTag("java"));
        verify(postMapper).countPublishedByTagSlug("java");
    }

    // ---- findPublishedByCategory ----

    @Test
    void findPublishedByCategory_mapsResults() {
        Page<PostDO> page = new Page<>(1, 10);
        page.setRecords(List.of(postRow(1L)));
        when(postMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var posts = repository.findPublishedByCategory(2L, 1, 10);

        assertEquals(1, posts.size());
        assertEquals(1L, posts.get(0).getId());
        verify(postMapper).selectPage(any(Page.class), any());
    }

    @Test
    void findPublishedByCategory_emptyResultReturnsEmpty() {
        Page<PostDO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(postMapper.selectPage(any(Page.class), any())).thenReturn(page);

        assertTrue(repository.findPublishedByCategory(2L, 1, 10).isEmpty());
    }

    // ---- countPublishedByCategory ----

    @Test
    void countPublishedByCategory_delegatesToMapper() {
        when(postMapper.selectCount(any())).thenReturn(7L);

        assertEquals(7L, repository.countPublishedByCategory(2L));
        verify(postMapper).selectCount(any());
    }

    // ---- findPage ----

    @Test
    void findPage_mapsRecordsToEntities() {
        Page<PostDO> page = new Page<>(1, 20);
        page.setRecords(List.of(postRow(1L), postRow(2L)));
        when(postMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var posts = repository.findPage(1, 20);

        assertEquals(2, posts.size());
        assertEquals(1L, posts.get(0).getId());
        assertEquals(2L, posts.get(1).getId());
    }

    @Test
    void findPage_emptyResultReturnsEmpty() {
        Page<PostDO> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(postMapper.selectPage(any(Page.class), any())).thenReturn(page);

        assertTrue(repository.findPage(1, 20).isEmpty());
    }

    // ---- countAll ----

    @Test
    void countAll_delegatesToMapper() {
        when(postMapper.selectCount(any())).thenReturn(100L);

        assertEquals(100L, repository.countAll());
        verify(postMapper).selectCount(any());
    }

    // ---- findPrevPublished ----

    @Test
    void findPrevPublished_returnsEntityWhenFound() {
        when(postMapper.findPrevPublished(5L)).thenReturn(postRow(3L));

        var result = repository.findPrevPublished(5L);

        assertTrue(result.isPresent());
        assertEquals(3L, result.get().getId());
    }

    @Test
    void findPrevPublished_returnsEmptyWhenNotFound() {
        when(postMapper.findPrevPublished(5L)).thenReturn(null);

        assertTrue(repository.findPrevPublished(5L).isEmpty());
    }

    // ---- findNextPublished ----

    @Test
    void findNextPublished_returnsEntityWhenFound() {
        when(postMapper.findNextPublished(5L)).thenReturn(postRow(7L));

        var result = repository.findNextPublished(5L);

        assertTrue(result.isPresent());
        assertEquals(7L, result.get().getId());
    }

    @Test
    void findNextPublished_returnsEmptyWhenNotFound() {
        when(postMapper.findNextPublished(5L)).thenReturn(null);

        assertTrue(repository.findNextPublished(5L).isEmpty());
    }

    // ---- setPostSeries ----

    @Test
    void setPostSeries_updatesPostDOWithSeriesInfo() {
        repository.setPostSeries(5L, 3L, 1);

        var captor = org.mockito.ArgumentCaptor.forClass(PostDO.class);
        verify(postMapper).updateById(captor.capture());
        assertEquals(5L, captor.getValue().getId());
        assertEquals(3L, captor.getValue().getSeriesId());
        assertEquals(1, captor.getValue().getSeriesOrder());
    }

    // ---- clearPostSeries ----

    @Test
    void clearPostSeries_delegatesToMapper() {
        repository.clearPostSeries(5L);

        verify(postMapper).clearPostSeries(5L);
    }

    // ---- findPublishedByAuthorId ----

    @Test
    void findPublishedByAuthorId_mapsResults() {
        Page<PostDO> page = new Page<>(1, 10);
        page.setRecords(List.of(postRow(1L)));
        when(postMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var posts = repository.findPublishedByAuthorId(7L, 1, 10);

        assertEquals(1, posts.size());
        assertEquals(1L, posts.get(0).getId());
        verify(postMapper).selectPage(any(Page.class), any());
    }

    @Test
    void findPublishedByAuthorId_emptyResultReturnsEmpty() {
        Page<PostDO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(postMapper.selectPage(any(Page.class), any())).thenReturn(page);

        assertTrue(repository.findPublishedByAuthorId(7L, 1, 10).isEmpty());
    }

    // ---- countPublishedByAuthorId ----

    @Test
    void countPublishedByAuthorId_delegatesToMapper() {
        when(postMapper.selectCount(any())).thenReturn(9L);

        assertEquals(9L, repository.countPublishedByAuthorId(7L));
        verify(postMapper).selectCount(any());
    }

    // ---- findBySlug ----

    @Test
    void findBySlug_returnsEntityWhenFound() {
        when(postMapper.selectOne(any())).thenReturn(postRow(1L));

        var result = repository.findBySlug("slug");

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("slug", result.get().getSlug());
    }

    @Test
    void findBySlug_returnsEmptyWhenNotFound() {
        when(postMapper.selectOne(any())).thenReturn(null);

        assertTrue(repository.findBySlug("missing").isEmpty());
    }

    // ---- updateSlug ----

    @Test
    void updateSlug_delegatesToMapper() {
        repository.updateSlug(5L, "new-slug");

        verify(postMapper).updateSlug(5L, "new-slug");
    }

    // ---- findAllPublished ----

    @Test
    void findAllPublished_mapsResults() {
        when(postMapper.selectList(any())).thenReturn(List.of(postRow(1L), postRow(2L)));

        var posts = repository.findAllPublished();

        assertEquals(2, posts.size());
        assertEquals(1L, posts.get(0).getId());
        assertEquals(2L, posts.get(1).getId());
    }

    @Test
    void findAllPublished_emptyResultReturnsEmpty() {
        when(postMapper.selectList(any())).thenReturn(List.of());

        assertTrue(repository.findAllPublished().isEmpty());
    }

    // ---- toEntity: seriesId null vs non-null branch ----

    @Test
    void toEntity_assignsSeriesWhenSeriesIdNotNull() {
        PostDO row = postRow(1L);
        row.setSeriesId(3L);
        row.setSeriesOrder(2);
        when(postMapper.selectById(1L)).thenReturn(row);

        var post = repository.findById(1L).orElseThrow();

        assertEquals(3L, post.getSeriesId());
        assertEquals(2, post.getSeriesOrder());
    }

    @Test
    void toEntity_doesNotAssignSeriesWhenSeriesIdNull() {
        PostDO row = postRow(1L);
        row.setSeriesId(null);
        row.setSeriesOrder(null);
        when(postMapper.selectById(1L)).thenReturn(row);

        var post = repository.findById(1L).orElseThrow();

        assertNull(post.getSeriesId());
        assertNull(post.getSeriesOrder());
    }

    // ---- helpers ----

    private PostDO postRow(Long id) {
        PostDO row = new PostDO();
        row.setId(id);
        row.setSlug("slug");
        row.setAuthorId(7L);
        row.setTitle("Title");
        row.setContent("Content");
        row.setStatus("PUBLISHED");
        row.setFeatured(false);
        row.setCreatedAt(LocalDateTime.now());
        row.setPublishedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setCategoryId(2L);
        return row;
    }

    private HotPostDO hotRow(Long id, Long viewCount) {
        HotPostDO hot = new HotPostDO();
        hot.setId(id);
        hot.setSlug("slug");
        hot.setAuthorId(7L);
        hot.setTitle("Title");
        hot.setContent("Content");
        hot.setStatus("PUBLISHED");
        hot.setFeatured(false);
        hot.setCreatedAt(LocalDateTime.now());
        hot.setPublishedAt(LocalDateTime.now());
        hot.setUpdatedAt(LocalDateTime.now());
        hot.setCategoryId(2L);
        hot.setViewCount(viewCount);
        return hot;
    }
}
