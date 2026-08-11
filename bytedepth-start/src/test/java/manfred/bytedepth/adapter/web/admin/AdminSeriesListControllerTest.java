package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.adapter.web.security.ContentOwnershipGuard;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.ui.ConcurrentModel;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminSeriesListControllerTest {

    private final SeriesRepository seriesRepository = Mockito.mock(SeriesRepository.class);
    private final ContentOwnershipGuard ownershipGuard = Mockito.mock(ContentOwnershipGuard.class);
    private final TestingAuthenticationToken authentication = new TestingAuthenticationToken("author", null);
    private final AdminSeriesListController controller = new AdminSeriesListController(seriesRepository, ownershipGuard);

    @Test
    void administratorList_includesEverySeries() {
        List<Series> all = List.of(Series.create("Java", "java", null, 1L));
        when(ownershipGuard.canManageSeries(authentication)).thenReturn(true);
        when(seriesRepository.findPage(null, 1, 20)).thenReturn(all);
        when(seriesRepository.count(null)).thenReturn(1L);
        ConcurrentModel model = new ConcurrentModel();

        assertEquals("admin/series/list", controller.list(authentication, model, null, 1, 20));
        assertEquals(all, model.getAttribute("seriesList"));
    }

    @Test
    void regularAuthorList_includesOnlyOwnSeries() {
        List<Series> own = List.of(Series.create("Java", "java", null, 7L));
        when(ownershipGuard.canManageSeries(authentication)).thenReturn(false);
        when(ownershipGuard.currentUserId(authentication)).thenReturn(7L);
        when(seriesRepository.findPageByAuthorId(7L, "Java", 2, 10)).thenReturn(own);
        when(seriesRepository.countByAuthorId(7L, "Java")).thenReturn(1L);
        ConcurrentModel model = new ConcurrentModel();

        assertEquals("admin/series/list", controller.list(authentication, model, "Java", 2, 10));
        assertEquals(own, model.getAttribute("seriesList"));
        assertEquals("/admin/series?name=Java&", model.getAttribute("filterBaseUrl"));
    }

    @Test
    void administratorList_omitsBlankNameFromPaginationUrl() {
        when(ownershipGuard.canManageSeries(authentication)).thenReturn(true);
        when(seriesRepository.findPage(" ", 1, 20)).thenReturn(List.of());
        when(seriesRepository.count(" ")).thenReturn(0L);
        ConcurrentModel model = new ConcurrentModel();

        controller.list(authentication, model, " ", 1, 20);

        assertEquals("/admin/series?", model.getAttribute("filterBaseUrl"));
    }

    @Test
    void create_assignsCurrentAuthorAndNormalizesBlankDescription() {
        when(seriesRepository.findBySlug("java")).thenReturn(Optional.empty());
        when(ownershipGuard.currentUserId(authentication)).thenReturn(7L);

        assertEquals("redirect:/admin/series", controller.create(authentication, "Java", "java", "  "));

        ArgumentCaptor<Series> saved = ArgumentCaptor.forClass(Series.class);
        verify(seriesRepository).save(saved.capture());
        assertEquals("Java", saved.getValue().getName());
        assertEquals("java", saved.getValue().getSlug());
        assertNull(saved.getValue().getDescription());
        assertEquals(7L, saved.getValue().getAuthorId());
    }

    @Test
    void create_keepsNonBlankDescription() {
        when(seriesRepository.findBySlug("spring")).thenReturn(Optional.empty());
        when(ownershipGuard.currentUserId(authentication)).thenReturn(7L);

        controller.create(authentication, "Spring", "spring", "Spring 基础");

        ArgumentCaptor<Series> saved = ArgumentCaptor.forClass(Series.class);
        verify(seriesRepository).save(saved.capture());
        assertEquals("Spring 基础", saved.getValue().getDescription());
    }

    @Test
    void create_acceptsMissingDescription() {
        when(seriesRepository.findBySlug("kotlin")).thenReturn(Optional.empty());
        when(ownershipGuard.currentUserId(authentication)).thenReturn(7L);

        controller.create(authentication, "Kotlin", "kotlin", null);

        ArgumentCaptor<Series> saved = ArgumentCaptor.forClass(Series.class);
        verify(seriesRepository).save(saved.capture());
        assertNull(saved.getValue().getDescription());
    }

    @Test
    void create_doesNotOverwriteAnExistingSlug() {
        when(seriesRepository.findBySlug("java")).thenReturn(Optional.of(Series.create("Java", "java", null, 1L)));

        assertEquals("redirect:/admin/series", controller.create(authentication, "Other", "java", "description"));
        Mockito.verify(seriesRepository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void delete_requiresSeriesOwnershipBeforeDeletingAssociations() {
        assertEquals("redirect:/admin/series", controller.delete(authentication, 3L));

        verify(ownershipGuard).requireSeriesOwner(authentication, 3L);
        verify(seriesRepository).deleteWithPosts(3L);
    }
}
