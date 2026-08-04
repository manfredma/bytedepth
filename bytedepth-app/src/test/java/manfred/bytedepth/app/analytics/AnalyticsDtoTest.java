package manfred.bytedepth.app.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsDtoTest {

    @Test
    void rankAndCountryDtos_coverDataContract() {
        PostViewRankDTO rank = new PostViewRankDTO();
        rank.setPostId(1L); rank.setPostTitle("post"); rank.setViewCount(2); rank.setPercent(3.5);
        CountryViewStatDTO country = new CountryViewStatDTO();
        country.setCountry("CN"); country.setViewCount(2); country.setPercent(3.5);
        TrendPointDTO trend = new TrendPointDTO();
        trend.setLabel("today"); trend.setViewCount(2);

        assertEquals(1L, rank.getPostId()); assertEquals("post", rank.getPostTitle());
        assertEquals(2, rank.getViewCount()); assertEquals(3.5, rank.getPercent());
        assertEquals("CN", country.getCountry()); assertEquals(2, country.getViewCount()); assertEquals(3.5, country.getPercent());
        assertEquals("today", trend.getLabel()); assertEquals(2, trend.getViewCount());
        assertEquals(rank, rank); assertNotEquals(rank, country); assertTrue(rank.toString().contains("postTitle=post"));
        assertEquals(country.hashCode(), country.hashCode()); assertTrue(trend.toString().contains("label=today"));
    }

    @Test
    void viewLogDto_coversEveryAccessorAndDataMethods() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 4, 12, 0);
        PostViewLogDTO log = new PostViewLogDTO();
        log.setId(1L); log.setPostId(2L); log.setUserId(3L); log.setIp("ip"); log.setUserAgent("ua");
        log.setReferer("ref"); log.setCountry("CN"); log.setCity("SZ"); log.setVisitedAt(time); log.setVisitToken("token");
        log.setActiveReadSeconds(4); log.setMaxScrollDepth(5); log.setLastActivityAt(time); log.setCompletedAt(time); log.setPostTitle("title");

        assertEquals(1L, log.getId()); assertEquals(2L, log.getPostId()); assertEquals(3L, log.getUserId());
        assertEquals("ip", log.getIp()); assertEquals("ua", log.getUserAgent()); assertEquals("ref", log.getReferer());
        assertEquals("CN", log.getCountry()); assertEquals("SZ", log.getCity()); assertEquals(time, log.getVisitedAt());
        assertEquals("token", log.getVisitToken()); assertEquals(4, log.getActiveReadSeconds()); assertEquals(5, log.getMaxScrollDepth());
        assertEquals(time, log.getLastActivityAt()); assertEquals(time, log.getCompletedAt()); assertEquals("title", log.getPostTitle());
        assertEquals(log, log); assertNotEquals(log, new PostViewLogDTO()); assertTrue(log.toString().contains("postTitle=title"));
    }
}
