package manfred.bytedepth.infrastructure.rating;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostRatingRepositoryImplTest {

    private final PostRatingMapper postRatingMapper = mock(PostRatingMapper.class);
    private final PostRatingRepositoryImpl repository = new PostRatingRepositoryImpl(postRatingMapper);

    @Test
    void upsert_delegatesToMapper() {
        repository.upsert(1L, "token-abc", 5);

        verify(postRatingMapper).upsert(1L, "token-abc", 5);
    }

    @Test
    void getStats_returnsAverageAndCountWhenDataPresent() {
        PostRatingStatsDO stats = new PostRatingStatsDO();
        stats.setAverageRating(4.5);
        stats.setRatingCount(10L);
        when(postRatingMapper.findStats(1L)).thenReturn(stats);

        var result = repository.getStats(1L);

        assertEquals(4.5, result.averageRating());
        assertEquals(10L, result.ratingCount());
    }

    @Test
    void getStats_returnsZeroAverageWhenNull() {
        PostRatingStatsDO stats = new PostRatingStatsDO();
        stats.setAverageRating(null);
        stats.setRatingCount(5L);
        when(postRatingMapper.findStats(2L)).thenReturn(stats);

        var result = repository.getStats(2L);

        assertEquals(0.0, result.averageRating());
        assertEquals(5L, result.ratingCount());
    }

    @Test
    void getStats_returnsZeroCountWhenNull() {
        PostRatingStatsDO stats = new PostRatingStatsDO();
        stats.setAverageRating(3.0);
        stats.setRatingCount(null);
        when(postRatingMapper.findStats(3L)).thenReturn(stats);

        var result = repository.getStats(3L);

        assertEquals(3.0, result.averageRating());
        assertEquals(0L, result.ratingCount());
    }

    @Test
    void getStats_returnsZerosWhenBothNull() {
        PostRatingStatsDO stats = new PostRatingStatsDO();
        stats.setAverageRating(null);
        stats.setRatingCount(null);
        when(postRatingMapper.findStats(4L)).thenReturn(stats);

        var result = repository.getStats(4L);

        assertEquals(0.0, result.averageRating());
        assertEquals(0L, result.ratingCount());
    }

    @Test
    void findScore_returnsScoreWhenPresent() {
        when(postRatingMapper.findScore(1L, "token-abc")).thenReturn(Optional.of(4));

        Optional<Integer> result = repository.findScore(1L, "token-abc");

        assertTrue(result.isPresent());
        assertEquals(4, result.orElseThrow());
    }

    @Test
    void findScore_returnsEmptyWhenNotFound() {
        when(postRatingMapper.findScore(1L, "missing")).thenReturn(Optional.empty());

        assertTrue(repository.findScore(1L, "missing").isEmpty());
    }
}
