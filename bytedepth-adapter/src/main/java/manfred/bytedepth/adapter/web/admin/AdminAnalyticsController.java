package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.analytics.CountryViewStatDTO;
import manfred.bytedepth.app.analytics.PageViewRankDTO;
import manfred.bytedepth.app.analytics.PageViewStatsPort;
import manfred.bytedepth.app.analytics.PostViewRankDTO;
import manfred.bytedepth.app.analytics.TrendPointDTO;
import manfred.bytedepth.app.analytics.TrendComparisonDTO;
import manfred.bytedepth.app.analytics.ViewLogStatsPort;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 访问统计分析后台。
 * 路径：GET /admin/analytics（页面）及 /admin/analytics/api/*（JSON）
 * 权限：由 SecurityConfig /admin/** 规则守卫（需 admin:dashboard:view）。
 */
@Controller
@RequestMapping("/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final ViewLogStatsPort viewLogStatsPort;
    private final PageViewStatsPort pageViewStatsPort;

    /** 页面骨架，数据全部由前端 AJAX 拉取。 */
    @GetMapping
    public String page() {
        return "admin/analytics";
    }

    @GetMapping("/api/top-posts")
    @ResponseBody
    public List<PostViewRankDTO> topPosts(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<PostViewRankDTO> rows = viewLogStatsPort.topPosts(start, end, limit);
        long total = rows.stream().mapToLong(PostViewRankDTO::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/countries")
    @ResponseBody
    public List<CountryViewStatDTO> countries(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<CountryViewStatDTO> rows = viewLogStatsPort.countryStats(start, end);
        long total = rows.stream().mapToLong(CountryViewStatDTO::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/country-posts")
    @ResponseBody
    public List<PostViewRankDTO> countryPosts(
            @RequestParam String country,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<PostViewRankDTO> rows = viewLogStatsPort.countryTopPosts(country, start, end, limit);
        long total = rows.stream().mapToLong(PostViewRankDTO::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/post-trend")
    @ResponseBody
    public TrendComparisonDTO postTrend(
            @RequestParam Long postId,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "auto") String granularity) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        String format = toDateFormat(start, end, hasExplicitRange(from, to), granularity);
        LocalDateTime previousStart = previousStart(start, end);
        LocalDateTime previousEnd = start.minusSeconds(1);
        return comparison(
                viewLogStatsPort.postTrend(postId, start, end, format),
                viewLogStatsPort.postTrend(postId, previousStart, previousEnd, format),
                start, end, previousStart, previousEnd, format);
    }

    @GetMapping("/api/overview-trend")
    @ResponseBody
    public TrendComparisonDTO overviewTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "auto") String granularity) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        String format = toDateFormat(start, end, hasExplicitRange(from, to), granularity);
        LocalDateTime previousStart = previousStart(start, end);
        LocalDateTime previousEnd = start.minusSeconds(1);
        return comparison(
                viewLogStatsPort.overviewTrend(start, end, format),
                viewLogStatsPort.overviewTrend(previousStart, previousEnd, format),
                start, end, previousStart, previousEnd, format);
    }

    // ── 页面统计 API ──────────────────────────────────────────────

    @GetMapping("/api/top-pages")
    @ResponseBody
    public List<PageViewRankDTO> topPages(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<PageViewRankDTO> rows = pageViewStatsPort.topPages(start, end, limit);
        long total = rows.stream().mapToLong(PageViewRankDTO::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/page-countries")
    @ResponseBody
    public List<CountryViewStatDTO> pageCountries(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<CountryViewStatDTO> rows = pageViewStatsPort.pageCountryStats(start, end);
        long total = rows.stream().mapToLong(CountryViewStatDTO::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/country-pages")
    @ResponseBody
    public List<PageViewRankDTO> countryPages(
            @RequestParam String country,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<PageViewRankDTO> rows = pageViewStatsPort.countryTopPages(country, start, end, limit);
        long total = rows.stream().mapToLong(PageViewRankDTO::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/page-trend")
    @ResponseBody
    public TrendComparisonDTO pageTrend(
            @RequestParam String pagePath,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "auto") String granularity) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        String format = toDateFormat(start, end, hasExplicitRange(from, to), granularity);
        LocalDateTime previousStart = previousStart(start, end);
        LocalDateTime previousEnd = start.minusSeconds(1);
        return comparison(
                pageViewStatsPort.pageTrend(pagePath, start, end, format),
                pageViewStatsPort.pageTrend(pagePath, previousStart, previousEnd, format),
                start, end, previousStart, previousEnd, format);
    }

    @GetMapping("/api/page-overview-trend")
    @ResponseBody
    public TrendComparisonDTO pageOverviewTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "auto") String granularity) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        String format = toDateFormat(start, end, hasExplicitRange(from, to), granularity);
        LocalDateTime previousStart = previousStart(start, end);
        LocalDateTime previousEnd = start.minusSeconds(1);
        return comparison(
                pageViewStatsPort.pageOverviewTrend(start, end, format),
                pageViewStatsPort.pageOverviewTrend(previousStart, previousEnd, format),
                start, end, previousStart, previousEnd, format);
    }

    // ── 工具方法（package-private 供测试直接调用）─────────────────────────

    static LocalDateTime toStartTime(String period, String from) {
        return toStartTime(period, from, LocalDate.now());
    }

    static LocalDateTime toStartTime(String period, String from, LocalDate today) {
        if (from != null && !from.isBlank()) {
            return LocalDate.parse(from).atStartOfDay();
        }
        return switch (period) {
            case "today" -> today.atStartOfDay();
            // 本月：自然月边界（本月 1 日 00:00），而非"过去 30 天"
            case "month" -> today.withDayOfMonth(1).atStartOfDay();
            // 本年：自然年边界（今年 1 月 1 日 00:00），而非"过去 365 天"
            case "year"  -> today.withDayOfYear(1).atStartOfDay();
            // 全部：从极早时间起，覆盖所有历史数据
            case "all"   -> LocalDate.of(2000, 1, 1).atStartOfDay();
            // 本周：自然周边界（本周一 00:00，周一为一周起始），而非"过去 7 天"
            default      -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        };
    }

    static LocalDateTime toEndTime(String period, String to) {
        return toEndTime(period, to, LocalDate.now());
    }

    static LocalDateTime toEndTime(String period, String to, LocalDate today) {
        if (to != null && !to.isBlank()) {
            return LocalDate.parse(to).atTime(23, 59, 59);
        }
        if ("today".equals(period)) {
            return today.atTime(23, 59, 59);
        }
        return switch (period) {
            case "week" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).atTime(23, 59, 59);
            case "month" -> today.with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59);
            case "year" -> today.with(TemporalAdjusters.lastDayOfYear()).atTime(23, 59, 59);
            default -> LocalDateTime.now();
        };
    }

    /** 按时间跨度自动选择 DATE_FORMAT 格式字符串。单日按小时展示，跨天时按日或月展示。 */
    static String toDateFormat(LocalDateTime start, LocalDateTime end) {
        return toDateFormat(start, end, false, "auto");
    }

    static String toDateFormat(LocalDateTime start, LocalDateTime end,
                               boolean explicitRange, String granularity) {
        if ("hour".equals(granularity)) return "%H:00";
        if ("day".equals(granularity)) return "%m-%d";
        if (start.toLocalDate().equals(end.toLocalDate())) {
            return "%H:00";
        }
        return end.toLocalDate().isAfter(start.toLocalDate().plusMonths(1)) ? "%Y-%m" : "%m-%d";
    }

    private static boolean hasExplicitRange(String from, String to) {
        return (from != null && !from.isBlank()) || (to != null && !to.isBlank());
    }

    /**
     * SQL 仅返回有访问的桶；在这里补齐零值桶，让坐标轴反映实际时间范围。
     */
    static List<TrendPointDTO> completeTrend(List<TrendPointDTO> source, LocalDateTime start,
                                          LocalDateTime end, String format) {
        Map<String, Long> counts = new HashMap<>();
        source.forEach(point -> counts.merge(point.getLabel(), point.getViewCount(), Long::sum));

        List<TrendPointDTO> result = new ArrayList<>();
        if ("%H:00".equals(format)) {
            LocalDateTime cursor = start.truncatedTo(ChronoUnit.HOURS);
            LocalDateTime last = end.truncatedTo(ChronoUnit.HOURS);
            while (!cursor.isAfter(last)) {
                addTrendPoint(result, cursor.format(DateTimeFormatter.ofPattern("HH:00")), counts);
                cursor = cursor.plusHours(1);
            }
        } else if ("%m-%d".equals(format)) {
            LocalDate cursor = start.toLocalDate();
            LocalDate last = end.toLocalDate();
            while (!cursor.isAfter(last)) {
                addTrendPoint(result, cursor.format(DateTimeFormatter.ofPattern("MM-dd")), counts);
                cursor = cursor.plusDays(1);
            }
        } else {
            YearMonth cursor = YearMonth.from(start);
            YearMonth last = YearMonth.from(end);
            while (!cursor.isAfter(last)) {
                addTrendPoint(result, cursor.toString(), counts);
                cursor = cursor.plusMonths(1);
            }
        }
        return result;
    }

    private static TrendComparisonDTO comparison(List<TrendPointDTO> currentSource,
                                                   List<TrendPointDTO> previousSource,
                                                   LocalDateTime start, LocalDateTime end,
                                                   LocalDateTime previousStart, LocalDateTime previousEnd,
                                                   String format) {
        List<TrendPointDTO> current = completeTrend(currentSource, start, end, format);
        List<TrendPointDTO> previousBuckets = completeTrend(previousSource, previousStart, previousEnd, format);

        // SQL 桶的日期标签不同；以当前标签逐索引映射才能在同一横轴逐桶比较。
        List<TrendPointDTO> previous = new ArrayList<>();
        for (int index = 0; index < current.size(); index++) {
            TrendPointDTO point = new TrendPointDTO();
            point.setLabel(current.get(index).getLabel());
            point.setViewCount(previousBuckets.get(index).getViewCount());
            previous.add(point);
        }

        TrendComparisonDTO result = new TrendComparisonDTO();
        result.setCurrent(current);
        result.setPrevious(previous);
        result.setCurrentPeriod(periodLabel(start, end));
        result.setPreviousPeriod(periodLabel(previousStart, previousEnd));
        return result;
    }

    private static LocalDateTime previousStart(LocalDateTime start, LocalDateTime end) {
        return start.minus(Duration.between(start, end).plusSeconds(1));
    }

    private static String periodLabel(LocalDateTime start, LocalDateTime end) {
        String startDate = start.toLocalDate().toString();
        String endDate = end.toLocalDate().toString();
        return startDate.equals(endDate) ? startDate : startDate + " 至 " + endDate;
    }

    private static void addTrendPoint(List<TrendPointDTO> result, String label, Map<String, Long> counts) {
        TrendPointDTO point = new TrendPointDTO();
        point.setLabel(label);
        point.setViewCount(counts.getOrDefault(label, 0L));
        result.add(point);
    }

    private static double pct(long value, long total) {
        if (total == 0) return 0.0;
        return Math.round(value * 1000.0 / total) / 10.0;
    }
}
