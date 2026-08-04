package manfred.bytedepth.infrastructure.comment;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentRepositoryImplTest {

    private final CommentMapper commentMapper = Mockito.mock(CommentMapper.class);
    private final CommentRepositoryImpl repository = new CommentRepositoryImpl(commentMapper);

    // ---- save ----

    @Test
    void save_insertsNewCommentWhenIdIsNull() {
        when(commentMapper.insert(any())).thenAnswer(invocation -> {
            invocation.<CommentDO>getArgument(0).setId(1L);
            return 1;
        });

        var comment = manfred.bytedepth.domain.comment.Comment.create(10L, 7L, "Alice", "Nice post");
        var saved = repository.save(comment);

        assertEquals(1L, saved.getId());
        assertEquals(10L, saved.getPostId());
        assertEquals(7L, saved.getAuthorId());
        assertEquals("Alice", saved.getAuthorName());
        assertEquals("Nice post", saved.getContent());
        assertEquals(manfred.bytedepth.domain.comment.CommentStatus.APPROVED, saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        verify(commentMapper).insert(any());
    }

    @Test
    void save_updatesExistingCommentWhenIdNotNull() {
        var comment = manfred.bytedepth.domain.comment.Comment.reconstruct(
            5L, 10L, 7L, "Alice", "Updated content",
            manfred.bytedepth.domain.comment.CommentStatus.APPROVED,
            LocalDateTime.now());

        var saved = repository.save(comment);

        assertEquals(5L, saved.getId());
        assertEquals("Updated content", saved.getContent());
        verify(commentMapper).updateById(any());
    }

    @Test
    void save_setsCreatedAtToNowWhenNull() {
        when(commentMapper.insert(any())).thenAnswer(invocation -> {
            invocation.<CommentDO>getArgument(0).setId(1L);
            return 1;
        });

        // Build a comment with null createdAt via reconstruct
        var comment = manfred.bytedepth.domain.comment.Comment.reconstruct(
            null, 10L, 7L, "Alice", "Content",
            manfred.bytedepth.domain.comment.CommentStatus.APPROVED, null);

        var saved = repository.save(comment);

        assertNotNull(saved.getCreatedAt());
    }

    // ---- findById ----

    @Test
    void findById_returnsEntityWhenFound() {
        CommentDO row = commentRow(1L);
        when(commentMapper.selectById(1L)).thenReturn(row);

        Optional<manfred.bytedepth.domain.comment.Comment> result = repository.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("Alice", result.get().getAuthorName());
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        when(commentMapper.selectById(99L)).thenReturn(null);

        assertTrue(repository.findById(99L).isEmpty());
    }

    @Test
    void findById_mapsNullAuthorId() {
        CommentDO row = commentRow(1L);
        row.setAuthorId(null);
        when(commentMapper.selectById(1L)).thenReturn(row);

        var result = repository.findById(1L).orElseThrow();

        assertEquals(null, result.getAuthorId());
        assertEquals("Alice", result.getAuthorName());
    }

    // ---- findApprovedByPostId ----

    @Test
    void findApprovedByPostId_mapsResults() {
        when(commentMapper.selectList(any())).thenReturn(List.of(commentRow(1L), commentRow(2L)));

        var comments = repository.findApprovedByPostId(10L);

        assertEquals(2, comments.size());
        assertEquals(1L, comments.get(0).getId());
        assertEquals(2L, comments.get(1).getId());
    }

    @Test
    void findApprovedByPostId_emptyResultReturnsEmpty() {
        when(commentMapper.selectList(any())).thenReturn(List.of());

        assertTrue(repository.findApprovedByPostId(10L).isEmpty());
    }

    // ---- findAll ----

    @Test
    void findAll_mapsResults() {
        Page<CommentDO> page = new Page<>(1, 20);
        page.setRecords(List.of(commentRow(1L)));
        when(commentMapper.selectPage(any(Page.class), any())).thenReturn(page);

        var comments = repository.findAll(1, 20);

        assertEquals(1, comments.size());
        assertEquals(1L, comments.get(0).getId());
        verify(commentMapper).selectPage(any(Page.class), any());
    }

    @Test
    void findAll_emptyResultReturnsEmpty() {
        Page<CommentDO> page = new Page<>(1, 20);
        page.setRecords(List.of());
        when(commentMapper.selectPage(any(Page.class), any())).thenReturn(page);

        assertTrue(repository.findAll(1, 20).isEmpty());
    }

    // ---- helpers ----

    private CommentDO commentRow(Long id) {
        CommentDO row = new CommentDO();
        row.setId(id);
        row.setPostId(10L);
        row.setAuthorId(7L);
        row.setAuthorName("Alice");
        row.setContent("Nice post");
        row.setStatus("APPROVED");
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }
}
