package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.analytics.CountryViewStatDTO;
import manfred.bytedepth.app.analytics.PostViewRankDTO;
import manfred.bytedepth.app.analytics.TrendPointDTO;
import manfred.bytedepth.app.analytics.ViewLogStatsPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = AdminAnalyticsController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
class AdminAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private PasswordEncoder passwordEncoder;
    @MockBean
    private ViewLogStatsPort viewLogStatsPort;

    // ── 认证守卫 ──────────────────────────────────────────

    @Test
    void analyticsPage_withoutAuth_deniesAccess() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void topPostsApi_withoutAuth_deniesAccess() throws Exception {
        mockMvc.perform(get("/admin/analytics/api/top-posts"))
                .andExpect(status().is4xxClientError());
    }

    // ── 页面端点 ──────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void analyticsPage_withAdmin_returnsAnalyticsView() throws Exception {
        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/analytics"));
    }

    // ── top-posts：percent 回填逻辑 ───────────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void topPosts_returnsJsonWithCorrectPercent() throws Exception {
        PostViewRankDTO r1 = new PostViewRankDTO();
        r1.setPostId(1L); r1.setPostTitle("Spring入门"); r1.setViewCount(80);
        PostViewRankDTO r2 = new PostViewRankDTO();
        r2.setPostId(2L); r2.setPostTitle("Docker实战"); r2.setViewCount(20);

        when(viewLogStatsPort.topPosts(any(), any(), eq(20)))
                .thenReturn(List.of(r1, r2));

        mockMvc.perform(get("/admin/analytics/api/top-posts").param("period", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].postId").value(1))
                .andExpect(jsonPath("$[0].percent").value(80.0))
                .andExpect(jsonPath("$[1].percent").value(20.0));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void topPosts_emptyResult_returnsEmptyArray() throws Exception {
        when(viewLogStatsPort.topPosts(any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api/top-posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ── top-posts：from/to 参数解析 ───────────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void topPosts_customFromParam_parsedAsStartOfDay() throws Exception {
        when(viewLogStatsPort.topPosts(any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api/top-posts")
                        .param("from", "2026-06-01")
                        .param("to", "2026-06-30"))
                .andExpect(status().isOk());

        ArgumentCaptor<LocalDateTime> startCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(viewLogStatsPort).topPosts(startCaptor.capture(), any(), anyInt());
        assertThat(startCaptor.getValue())
                .isEqualTo(LocalDate.of(2026, 6, 1).atStartOfDay());
    }

    // ── countries ─────────────────────────────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void countries_returnsJsonWithPercent() throws Exception {
        CountryViewStatDTO cn = new CountryViewStatDTO();
        cn.setCountry("中国"); cn.setViewCount(60);
        CountryViewStatDTO us = new CountryViewStatDTO();
        us.setCountry("美国"); us.setViewCount(40);

        when(viewLogStatsPort.countryStats(any(), any())).thenReturn(List.of(cn, us));

        mockMvc.perform(get("/admin/analytics/api/countries").param("period", "month"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].country").value("中国"))
                .andExpect(jsonPath("$[0].percent").value(60.0))
                .andExpect(jsonPath("$[1].percent").value(40.0));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void countries_withZeroTotal_returnsZeroPercent() throws Exception {
        CountryViewStatDTO country = new CountryViewStatDTO();
        country.setCountry("未知");
        country.setViewCount(0L);
        when(viewLogStatsPort.countryStats(any(), any())).thenReturn(List.of(country));

        mockMvc.perform(get("/admin/analytics/api/countries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].percent").value(0.0));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void countryPosts_returnsJsonWithPercent() throws Exception {
        PostViewRankDTO rank = new PostViewRankDTO();
        rank.setPostId(3L);
        rank.setPostTitle("Java");
        rank.setViewCount(10L);
        when(viewLogStatsPort.countryTopPosts(eq("中国"), any(), any(), eq(20)))
                .thenReturn(List.of(rank));

        mockMvc.perform(get("/admin/analytics/api/country-posts").param("country", "中国"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].postId").value(3))
                .andExpect(jsonPath("$[0].percent").value(100.0));
    }

    // ── post-trend：format 由时间跨度决定 ─────────────────

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void postTrend_oneWeekSpan_usesDayFormat() throws Exception {
        when(viewLogStatsPort.postTrend(any(), any(), any(), any()))
                .thenReturn(List.of());

        // 用显式 from/to 指定一周跨度，避免依赖"今天是周几"导致边界不稳定
        mockMvc.perform(get("/admin/analytics/api/post-trend")
                        .param("postId", "42")
                        .param("from", "2026-06-30")
                        .param("to", "2026-07-06"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> fmtCaptor = ArgumentCaptor.forClass(String.class);
        verify(viewLogStatsPort).postTrend(eq(42L), any(), any(), fmtCaptor.capture());
        assertThat(fmtCaptor.getValue()).isEqualTo("%m-%d");
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void postTrend_yearPeriod_usesMonthFormat() throws Exception {
        when(viewLogStatsPort.postTrend(any(), any(), any(), any()))
                .thenReturn(List.of());

        mockMvc.perform(get("/admin/analytics/api/post-trend")
                        .param("postId", "42")
                        .param("period", "year"))
                .andExpect(status().isOk());

        ArgumentCaptor<String> fmtCaptor = ArgumentCaptor.forClass(String.class);
        verify(viewLogStatsPort).postTrend(eq(42L), any(), any(), fmtCaptor.capture());
        assertThat(fmtCaptor.getValue()).isEqualTo("%Y-%m");
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void postTrend_returnsCompletedTrend() throws Exception {
        when(viewLogStatsPort.postTrend(any(), any(), any(), eq("%m-%d")))
                .thenReturn(List.of(trendPoint("07-02", 3)));

        mockMvc.perform(get("/admin/analytics/api/post-trend")
                        .param("postId", "42")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[1].label").value("07-02"))
                .andExpect(jsonPath("$[1].viewCount").value(3));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void overviewTrend_forOneDay_fillsEveryHourFromMidnight() throws Exception {
        when(viewLogStatsPort.overviewTrend(any(), any(), eq("%H:00")))
                .thenReturn(List.of(trendPoint("02:00", 4)));

        mockMvc.perform(get("/admin/analytics/api/overview-trend")
                        .param("from", "2026-07-29")
                        .param("to", "2026-07-29"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(24))
                .andExpect(jsonPath("$[0].label").value("00:00"))
                .andExpect(jsonPath("$[0].viewCount").value(0))
                .andExpect(jsonPath("$[2].label").value("02:00"))
                .andExpect(jsonPath("$[2].viewCount").value(4))
                .andExpect(jsonPath("$[23].label").value("23:00"));
    }

    @Test
    @WithMockUser(authorities = {"admin:dashboard:view"})
    void overviewTrend_forMultipleDays_usesDatesAndFillsMissingDays() throws Exception {
        when(viewLogStatsPort.overviewTrend(any(), any(), eq("%m-%d")))
                .thenReturn(List.of(trendPoint("07-02", 3)));

        mockMvc.perform(get("/admin/analytics/api/overview-trend")
                        .param("from", "2026-06-30")
                        .param("to", "2026-07-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].label").value("06-30"))
                .andExpect(jsonPath("$[0].viewCount").value(0))
                .andExpect(jsonPath("$[2].label").value("07-02"))
                .andExpect(jsonPath("$[2].viewCount").value(3))
                .andExpect(jsonPath("$[6].label").value("07-06"));
    }

    // ── 静态工具方法单元测试 ───────────────────────────────

    @Test
    void toStartTime_weekPeriod_returnsStartOfThisWeek() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("week", null);
        // 本周一 00:00（周一为一周起始）
        assertThat(result.toLocalDate().getDayOfWeek())
                .isEqualTo(java.time.DayOfWeek.MONDAY);
        assertThat(result.toLocalTime()).isEqualTo(java.time.LocalTime.MIN);
        assertThat(result).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(result).isAfter(LocalDateTime.now().minusDays(8));
    }

    @Test
    void toStartTime_customFrom_parsesDate() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("week", "2026-06-01");
        assertThat(result).isEqualTo(LocalDateTime.of(2026, 6, 1, 0, 0, 0));
    }

    @Test
    void toStartTime_today_returnsStartOfToday() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("today", null);
        assertThat(result).isEqualTo(LocalDate.now().atStartOfDay());
    }

    @Test
    void toStartTime_monthPeriod_returnsFirstDayOfThisMonth() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("month", null);
        assertThat(result).isEqualTo(LocalDate.now().withDayOfMonth(1).atStartOfDay());
    }

    @Test
    void toStartTime_yearPeriod_returnsFirstDayOfThisYear() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("year", null);
        assertThat(result).isEqualTo(LocalDate.now().withDayOfYear(1).atStartOfDay());
    }

    @Test
    void toStartTime_allPeriod_returnsEarlyEpoch() {
        LocalDateTime result = AdminAnalyticsController.toStartTime("all", null);
        // "全部"应覆盖所有历史数据，起点远早于任何真实访问日志
        assertThat(result).isBefore(LocalDateTime.of(2010, 1, 1, 0, 0));
    }

    @Test
    void toEndTime_usesExplicitEndOfDayAndCurrentTimeForTodayOrOtherPeriods() {
        assertThat(AdminAnalyticsController.toEndTime("week", "2026-06-30"))
                .isEqualTo(LocalDateTime.of(2026, 6, 30, 23, 59, 59));
        assertThat(AdminAnalyticsController.toEndTime("today", null)).isBeforeOrEqualTo(LocalDateTime.now());
        assertThat(AdminAnalyticsController.toEndTime("week", null)).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void toDateFormat_within2Days_returnsHourFormat() {
        LocalDateTime start = LocalDateTime.now().minusHours(10);
        assertThat(AdminAnalyticsController.toDateFormat(start, LocalDateTime.now()))
                .isEqualTo("%H:00");
    }

    @Test
    void toDateFormat_acrossTwoDates_returnsDayFormatToAvoidMergingHours() {
        assertThat(AdminAnalyticsController.toDateFormat(
                LocalDateTime.of(2026, 7, 28, 0, 0),
                LocalDateTime.of(2026, 7, 29, 12, 0)))
                .isEqualTo("%m-%d");
    }

    @Test
    void toDateFormat_30Days_returnsDayFormat() {
        LocalDateTime start = LocalDateTime.now().minusDays(30);
        assertThat(AdminAnalyticsController.toDateFormat(start, LocalDateTime.now()))
                .isEqualTo("%m-%d");
    }

    @Test
    void toDateFormat_365Days_returnsMonthFormat() {
        LocalDateTime start = LocalDateTime.now().minusDays(365);
        assertThat(AdminAnalyticsController.toDateFormat(start, LocalDateTime.now()))
                .isEqualTo("%Y-%m");
    }

    private static TrendPointDTO trendPoint(String label, long viewCount) {
        TrendPointDTO point = new TrendPointDTO();
        point.setLabel(label);
        point.setViewCount(viewCount);
        return point;
    }
}
