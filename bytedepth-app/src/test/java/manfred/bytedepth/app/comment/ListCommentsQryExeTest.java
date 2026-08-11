package manfred.bytedepth.app.comment;

import manfred.bytedepth.domain.comment.Comment;
import manfred.bytedepth.domain.comment.CommentRepository;
import manfred.bytedepth.domain.comment.CommentStatus;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ListCommentsQryExeTest {

    private final CommentRepository comments = mock(CommentRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final ListCommentsQryExe query = new ListCommentsQryExe(comments, posts);

    @Test
    void approvedComments_areMappedWithoutLookingUpPosts() {
        Comment comment = comment(1L, 2L);
        when(comments.findApprovedByPostId(2L)).thenReturn(List.of(comment));

        CommentDTO dto = query.findApprovedByPostId(2L).getFirst();

        assertEquals("alice", dto.getAuthorName());
        assertEquals("APPROVED", dto.getStatus());
        verifyNoInteractions(posts);
    }

    @Test
    void administratorList_resolvesPresentAndMissingPostSlugs() {
        when(comments.findAll(2, 10)).thenReturn(List.of(comment(1L, 3L), comment(2L, 4L)));
        when(posts.findById(3L)).thenReturn(Optional.of(Post.reconstruct(3L, "post-3", "t", "c", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false)));
        when(posts.findById(4L)).thenReturn(Optional.empty());

        List<CommentDTO> result = query.findAll(2, 10);

        assertEquals("post-3", result.get(0).getPostSlug());
        assertEquals("", result.get(1).getPostSlug());
    }

    @Test
    void findPage_withFilters_delegatesFilteredQueryAndReturnsTotal() {
        when(comments.findAll(1, 50, "alice", null)).thenReturn(List.of(comment(1L, 3L)));
        when(comments.countFiltered("alice", null)).thenReturn(5L);
        when(posts.findById(3L)).thenReturn(Optional.of(Post.reconstruct(3L, "post-3", "t", "c", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false)));

        ListCommentsQryExe.PageResult result = query.findPage(1, 50, "alice", null);

        assertEquals(5L, result.total());
        assertEquals("post-3", result.comments().get(0).getPostSlug());
        verify(comments).findAll(1, 50, "alice", null);
        verify(comments).countFiltered("alice", null);
    }

    @Test
    void findPage_withPostIdFilter_passesPostId() {
        when(comments.findAll(1, 50, null, 7L)).thenReturn(List.of());
        when(comments.countFiltered(null, 7L)).thenReturn(0L);

        ListCommentsQryExe.PageResult result = query.findPage(1, 50, null, 7L);

        assertEquals(0L, result.total());
        verify(comments).findAll(1, 50, null, 7L);
        verify(comments).countFiltered(null, 7L);
    }

    private Comment comment(Long id, Long postId) {
        return Comment.reconstruct(id, postId, 9L, "alice", "hello", CommentStatus.APPROVED,
                LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
