package manfred.bytedepth.app.series;

import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetPostSeriesCmdExeTest {

    private final PostRepository postRepository = Mockito.mock(PostRepository.class);
    private final SeriesRepository seriesRepository = Mockito.mock(SeriesRepository.class);
    private final SetPostSeriesCmdExe command = new SetPostSeriesCmdExe(postRepository, seriesRepository);

    @Test
    void missingSeries_isCreatedForTheRequestingAdministrator() {
        when(seriesRepository.findBySlug("java")).thenReturn(Optional.empty());
        when(seriesRepository.save(Mockito.any())).thenReturn(Series.reconstruct(3L, "Java", "java", null, 1L));

        command.execute(9L, "java", "Java", 2, 1L);

        ArgumentCaptor<Series> created = ArgumentCaptor.forClass(Series.class);
        verify(seriesRepository).save(created.capture());
        assertEquals(1L, created.getValue().getAuthorId());
        verify(postRepository).setPostSeries(9L, 3L, 2);
    }

    @Test
    void existingSeries_isReusedWithoutCreatingAnotherOne() {
        when(seriesRepository.findBySlug("java"))
                .thenReturn(Optional.of(Series.reconstruct(3L, "Java", "java", null, 1L)));

        command.execute(9L, "java", null, 2, 1L);

        Mockito.verify(seriesRepository, Mockito.never()).save(Mockito.any());
        verify(postRepository).setPostSeries(9L, 3L, 2);
    }

    @Test
    void missingSeries_usesSlugWhenNoNameIsProvided() {
        when(seriesRepository.findBySlug("java")).thenReturn(Optional.empty());
        when(seriesRepository.save(Mockito.any())).thenReturn(Series.reconstruct(3L, "java", "java", null, 1L));

        command.execute(9L, "java", null, 2, 1L);

        ArgumentCaptor<Series> created = ArgumentCaptor.forClass(Series.class);
        verify(seriesRepository).save(created.capture());
        assertEquals("java", created.getValue().getName());
    }
}
