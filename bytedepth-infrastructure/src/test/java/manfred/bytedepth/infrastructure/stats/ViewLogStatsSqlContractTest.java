package manfred.bytedepth.infrastructure.stats;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ViewLogStatsSqlContractTest {

    @Test
    void analyticsSqlReadsHourlyAndDailyAggregatesAlongsideRawRows() throws IOException {
        String postSql = readResource("/mapper/ViewLogStatsMapper.xml");
        String pageSql = readResource("/mapper/PageViewStatsMapper.xml");

        assertThat(postSql).contains("post_view_hourly_stat", "post_view_country_daily_stat", "post_view_log");
        assertThat(pageSql).contains("page_view_hourly_stat", "page_view_country_daily_stat", "page_view_log");
        assertThat(postSql + pageSql).doesNotContain("user_agent", "referer", "ip");
    }

    @Test
    void analyticsSqlUsesHalfOpenEndPredicatesForRawRows() throws IOException {
        String sql = readResource("/mapper/ViewLogStatsMapper.xml")
                + readResource("/mapper/PageViewStatsMapper.xml");

        assertThat(sql).doesNotContain("&lt;= #{endTime}", "&lt;=#{endTime}");
        assertThat(sql).contains("&lt; DATE_ADD(#{endTime}, INTERVAL 1 SECOND)");
    }

    @Test
    void countryUnionNormalizesCollationAcrossArchivedAndRawRows() throws IOException {
        String postSql = readResource("/mapper/ViewLogStatsMapper.xml");
        String pageSql = readResource("/mapper/PageViewStatsMapper.xml");

        assertThat(postSql).contains(
                "SELECT country COLLATE utf8mb4_unicode_ci AS country",
                "SELECT COALESCE(NULLIF(country, ''), '未知') COLLATE utf8mb4_unicode_ci AS country",
                "GROUP BY COALESCE(NULLIF(country, ''), '未知') COLLATE utf8mb4_unicode_ci");
        assertThat(pageSql).contains(
                "SELECT country COLLATE utf8mb4_unicode_ci AS country",
                "SELECT COALESCE(NULLIF(country, ''), '未知') COLLATE utf8mb4_unicode_ci AS country",
                "GROUP BY COALESCE(NULLIF(country, ''), '未知') COLLATE utf8mb4_unicode_ci");
    }

    private String readResource(String path) throws IOException {
        try (var stream = getClass().getResourceAsStream(path)) {
            assertThat(stream).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
