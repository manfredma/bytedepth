package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.adapter.web.security.ContentOwnershipGuard;
import manfred.bytedepth.app.series.AppendPostToSeriesCmdExe;
import manfred.bytedepth.app.series.GetSeriesDetailQryExe;
import manfred.bytedepth.app.series.MovePostInSeriesCmdExe;
import manfred.bytedepth.app.series.RemovePostFromSeriesCmdExe;
import manfred.bytedepth.app.series.SeriesDetailDTO;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.ui.ConcurrentModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSeriesDetailControllerTest {

    private final GetSeriesDetailQryExe getSeriesDetailQryExe = Mockito.mock(GetSeriesDetailQryExe.class);
    private final AppendPostToSeriesCmdExe appendPostToSeriesCmdExe = Mockito.mock(AppendPostToSeriesCmdExe.class);
    private final RemovePostFromSeriesCmdExe removePostFromSeriesCmdExe = Mockito.mock(RemovePostFromSeriesCmdExe.class);
    private final MovePostInSeriesCmdExe movePostInSeriesCmdExe = Mockito.mock(MovePostInSeriesCmdExe.class);
    private final SeriesRepository seriesRepository = Mockito.mock(SeriesRepository.class);
    private final ContentOwnershipGuard ownershipGuard = Mockito.mock(ContentOwnershipGuard.class);
    private final TestingAuthenticationToken authentication = new TestingAuthenticationToken("author", null);
    private final AdminSeriesDetailController controller = new AdminSeriesDetailController(getSeriesDetailQryExe,
            appendPostToSeriesCmdExe, removePostFromSeriesCmdExe, movePostInSeriesCmdExe, seriesRepository, ownershipGuard);

    @BeforeEach
    void setUpSeries() {
        SeriesDetailDTO series = new SeriesDetailDTO();
        series.setId(3L);
        series.setSlug("java");
        when(getSeriesDetailQryExe.execute("java")).thenReturn(series);
    }

    @Test
    void regularAuthor_detailUsesOnlyOwnCandidatePosts() {
        when(ownershipGuard.canManageSeries(authentication)).thenReturn(false);
        when(ownershipGuard.currentUserId(authentication)).thenReturn(7L);
        when(seriesRepository.countCandidatesForSeriesByAuthor(3L, 7L, "spring")).thenReturn(1L);
        when(seriesRepository.findCandidatesForSeriesByAuthor(3L, 7L, "spring", 2, 10))
                .thenReturn(List.of(new SeriesPostItem(11L, "post", "标题", 1, null, null, null)));
        ConcurrentModel model = new ConcurrentModel();

        assertEquals("admin/series/detail", controller.detail(authentication, "java", 2, "spring", model));
        assertEquals(1L, model.getAttribute("candidateTotal"));
        assertEquals(1L, model.getAttribute("candidateTotalPages"));
        verify(ownershipGuard).requireSeriesOwner(authentication, 3L);
        verify(seriesRepository).findCandidatesForSeriesByAuthor(3L, 7L, "spring", 2, 10);
    }

    @Test
    void administrator_detailCanSeeAllCandidatePosts() {
        when(ownershipGuard.canManageSeries(authentication)).thenReturn(true);
        when(seriesRepository.countCandidatesForSeries(3L, "")).thenReturn(0L);
        when(seriesRepository.findCandidatesForSeries(3L, "", 1, 10))
                .thenReturn(List.of(new SeriesPostItem(12L, "post", "标题", 1, null, "DRAFT", null)));

        assertEquals("admin/series/detail", controller.detail(authentication, "java", 1, "", new ConcurrentModel()));
        verify(seriesRepository).findCandidatesForSeries(3L, "", 1, 10);
    }

    @Test
    void contentOperationsRequireOwnershipOfBothSeriesAndPost() {
        assertEquals("redirect:/admin/series/java?candidatePage=2&q=spring",
                controller.appendPost(authentication, "java", 9L, 2, "spring"));
        assertEquals("redirect:/admin/series/java", controller.removePost(authentication, "java", 9L));
        assertEquals("redirect:/admin/series/java", controller.moveUp(authentication, "java", 9L));
        assertEquals("redirect:/admin/series/java", controller.moveDown(authentication, "java", 9L));

        verify(appendPostToSeriesCmdExe).execute(9L, 3L);
        verify(removePostFromSeriesCmdExe).execute(9L);
        verify(movePostInSeriesCmdExe).execute(3L, 9L, MovePostInSeriesCmdExe.Direction.UP);
        verify(movePostInSeriesCmdExe).execute(3L, 9L, MovePostInSeriesCmdExe.Direction.DOWN);
        verify(ownershipGuard, Mockito.times(4)).requireSeriesOwner(authentication, 3L);
        verify(ownershipGuard, Mockito.times(4)).requirePostOwner(authentication, 9L);
    }
}
