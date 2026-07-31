package manfred.bytedepth.app.post.query;

import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.AuthorPostRepository;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListAllPostsQryExeTest {

    private final PostRepository postRepository = Mockito.mock(PostRepository.class);
    private final AuthorPostRepository authorPostRepository = Mockito.mock(AuthorPostRepository.class);
    private final SeriesRepository seriesRepository = Mockito.mock(SeriesRepository.class);
    private final ListAllPostsQryExe query = new ListAllPostsQryExe(postRepository, authorPostRepository, seriesRepository);

    @Test
    void execute_returnsEveryNonDeletedPostForAdministrators() {
        Post post = post(1L, null);
        when(postRepository.findPage(2, 5)).thenReturn(List.of(post));
        when(postRepository.countAll()).thenReturn(8L);

        ListAllPostsQryExe.PageResult result = query.execute(2, 5);

        assertEquals(8L, result.total());
        assertEquals(1L, result.posts().getFirst().getId());
        verify(postRepository).findPage(2, 5);
    }

    @Test
    void executeByAuthor_queriesOnlyTheCurrentAuthorsPostsAndResolvesOwnSeries() {
        Post post = post(2L, 3L);
        when(authorPostRepository.findPageByAuthorId(7L, 1, 20)).thenReturn(List.of(post));
        when(authorPostRepository.countByAuthorId(7L)).thenReturn(1L);
        when(seriesRepository.findById(3L)).thenReturn(Optional.of(Series.reconstruct(3L, "Java", "java", null, 7L)));

        ListAllPostsQryExe.PageResult result = query.executeByAuthor(7L, 1, 20);

        assertEquals(1L, result.total());
        assertEquals("Java", result.posts().getFirst().getSeriesName());
        assertEquals("java", result.posts().getFirst().getSeriesSlug());
        verify(authorPostRepository).findPageByAuthorId(7L, 1, 20);
        verify(authorPostRepository).countByAuthorId(7L);
    }

    private Post post(Long id, Long seriesId) {
        Post post = Post.reconstruct(id, "post-" + id, "标题", "内容", PostStatus.DRAFT,
                LocalDateTime.now(), null, LocalDateTime.now(), null, 7L, false);
        if (seriesId != null) {
            post.assignSeries(seriesId, 1);
        }
        return post;
    }
}
