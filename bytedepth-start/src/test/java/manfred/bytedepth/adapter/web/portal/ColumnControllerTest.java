package manfred.bytedepth.adapter.web.portal;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.series.GetSeriesForPortalQryExe;
import manfred.bytedepth.app.series.ListSeriesQryExe;
import manfred.bytedepth.app.series.SeriesPortalDTO;
import manfred.bytedepth.app.series.SeriesPortalPostDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = ColumnController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class, DataSourceAutoConfiguration.class})
class ColumnControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private ListSeriesQryExe listSeriesQryExe;
    @MockitoBean private VisitRequestFilter visitRequestFilter;
    @MockitoBean private GetSeriesForPortalQryExe getSeriesForPortalQryExe;

    @Test
    void detailFirstPageShowsPositionalOrderStartingFromOne() throws Exception {
        // series_order 存在空洞（4、5、6），展示序号仍应为 1、2、3
        SeriesPortalDTO series = portalSeries("engineering-practice", "工程实践", 1, 3, List.of(
                post(76L, "first-post", "第一篇", 4),
                post(77L, "second-post", "第二篇", 5),
                post(78L, "third-post", "第三篇", 6)));
        when(getSeriesForPortalQryExe.execute(eq("engineering-practice"), eq(1))).thenReturn(series);

        mockMvc.perform(get("/columns/engineering-practice"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/columns/detail"))
                .andExpect(content().string(containsString("<span class=\"post-order\">1.</span>")))
                .andExpect(content().string(containsString("<span class=\"post-order\">2.</span>")))
                .andExpect(content().string(containsString("<span class=\"post-order\">3.</span>")))
                .andExpect(content().string(not(containsString("<span class=\"post-order\">4.</span>"))))
                .andExpect(content().string(not(containsString("<span class=\"post-order\">5.</span>"))))
                .andExpect(content().string(not(containsString("<span class=\"post-order\">6.</span>"))));
    }

    @Test
    void detailSecondPageKeepsGlobalPositionalOrder() throws Exception {
        // 第 2 页：每页 10，本页 2 篇 series_order=14、15，全局位置应为 11、12
        SeriesPortalDTO series = portalSeries("engineering-practice", "工程实践", 2, 2, List.of(
                post(86L, "eleventh-post", "第十一篇", 14),
                post(87L, "twelfth-post", "第十二篇", 15)));
        when(getSeriesForPortalQryExe.execute(eq("engineering-practice"), eq(2))).thenReturn(series);

        mockMvc.perform(get("/columns/engineering-practice?page=2"))
                .andExpect(status().isOk())
                .andExpect(view().name("public/columns/detail"))
                .andExpect(content().string(containsString("<span class=\"post-order\">11.</span>")))
                .andExpect(content().string(containsString("<span class=\"post-order\">12.</span>")))
                .andExpect(content().string(not(containsString("<span class=\"post-order\">1.</span>"))));
    }

    private static SeriesPortalDTO portalSeries(String slug, String name, int currentPage,
                                                long totalPosts, List<SeriesPortalPostDTO> posts) {
        SeriesPortalDTO series = new SeriesPortalDTO();
        series.setId(13L);
        series.setSlug(slug);
        series.setName(name);
        series.setDescription(null);
        series.setPosts(posts);
        series.setTotalPosts(totalPosts);
        series.setCurrentPage(currentPage);
        series.setTotalPages(1);
        return series;
    }

    private static SeriesPortalPostDTO post(Long id, String slug, String title, int seriesOrder) {
        SeriesPortalPostDTO dto = new SeriesPortalPostDTO();
        dto.setId(id);
        dto.setSlug(slug);
        dto.setTitle(title);
        dto.setSeriesOrder(seriesOrder);
        return dto;
    }
}
