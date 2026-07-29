package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.infrastructure.stats.ViewLogStatsMapper;
import manfred.bytedepth.infrastructure.stats.dto.CountryViewStat;
import manfred.bytedepth.infrastructure.stats.dto.PostViewRank;
import manfred.bytedepth.infrastructure.stats.dto.TrendPoint;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.DayOfWeek;
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

    private final ViewLogStatsMapper viewLogStatsMapper;

    /** 页面骨架，数据全部由前端 AJAX 拉取。 */
    @GetMapping
    public String page() {
        return "admin/analytics";
    }

    @GetMapping("/api/top-posts")
    @ResponseBody
    public List<PostViewRank> topPosts(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<PostViewRank> rows = viewLogStatsMapper.topPosts(start, end, limit);
        long total = rows.stream().mapToLong(PostViewRank::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/countries")
    @ResponseBody
    public List<CountryViewStat> countries(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<CountryViewStat> rows = viewLogStatsMapper.countryStats(start, end);
        long total = rows.stream().mapToLong(CountryViewStat::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/country-posts")
    @ResponseBody
    public List<PostViewRank> countryPosts(
            @RequestParam String country,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        List<PostViewRank> rows = viewLogStatsMapper.countryTopPosts(country, start, end, limit);
        long total = rows.stream().mapToLong(PostViewRank::getViewCount).sum();
        rows.forEach(r -> r.setPercent(pct(r.getViewCount(), total)));
        return rows;
    }

    @GetMapping("/api/post-trend")
    @ResponseBody
    public List<TrendPoint> postTrend(
            @RequestParam Long postId,
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        String format = toDateFormat(start, end);
        return completeTrend(viewLogStatsMapper.postTrend(postId, start, end, format), start, end, format);
    }

    @GetMapping("/api/overview-trend")
    @ResponseBody
    public List<TrendPoint> overviewTrend(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        LocalDateTime start = toStartTime(period, from);
        LocalDateTime end   = toEndTime(period, to);
        String format = toDateFormat(start, end);
        return completeTrend(viewLogStatsMapper.overviewTrend(start, end, format), start, end, format);
    }

    // ── 工具方法（package-private 供测试直接调用）─────────────────────────

    static LocalDateTime toStartTime(String period, String from) {
        if (from != null && !from.isBlank()) {
            return LocalDate.parse(from).atStartOfDay();
        }
        LocalDate today = LocalDate.now();
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
        if (to != null && !to.isBlank()) {
            return LocalDate.parse(to).atTime(23, 59, 59);
        }
        if ("today".equals(period)) {
            return LocalDateTime.now();
        }
        return LocalDateTime.now();
    }

    /** 按时间跨度自动选择 DATE_FORMAT 格式字符串。跨天时绝不按小时聚合，避免相同小时被合并。 */
    static String toDateFormat(LocalDateTime start, LocalDateTime end) {
        if (start.toLocalDate().equals(end.toLocalDate())) return "%H:00";
        long days = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate());
        if (days <= 60) return "%m-%d";
        return "%Y-%m";
    }

    /**
     * SQL 仅返回有访问的桶；在这里补齐零值桶，让坐标轴反映实际时间范围。
     */
    static List<TrendPoint> completeTrend(List<TrendPoint> source, LocalDateTime start,
                                          LocalDateTime end, String format) {
        Map<String, Long> counts = new HashMap<>();
        source.forEach(point -> counts.merge(point.getLabel(), point.getViewCount(), Long::sum));

        List<TrendPoint> result = new ArrayList<>();
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

    private static void addTrendPoint(List<TrendPoint> result, String label, Map<String, Long> counts) {
        TrendPoint point = new TrendPoint();
        point.setLabel(label);
        point.setViewCount(counts.getOrDefault(label, 0L));
        result.add(point);
    }

    private static double pct(long value, long total) {
        if (total == 0) return 0.0;
        return Math.round(value * 1000.0 / total) / 10.0;
    }
}
