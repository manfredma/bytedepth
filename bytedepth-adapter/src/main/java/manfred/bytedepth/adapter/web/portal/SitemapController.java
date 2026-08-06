package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

/**
 * 动态生成 sitemap.xml，供 Google 等搜索引擎发现所有已发布页面。
 * 路径：/sitemap.xml
 */
@Controller
@RequiredArgsConstructor
public class SitemapController {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${bytedepth.site.url}")
    private String siteUrl;

    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String sitemap() {
        List<Post> posts = postRepository.findAllPublished();
        List<Series> seriesList = seriesRepository.findAll();
        Optional<LocalDateTime> latestPostChange = latestChange(posts);
        Map<Long, LocalDateTime> latestSeriesPostChanges = posts.stream()
                .filter(post -> post.getSeriesId() != null)
                .filter(post -> lastChangedAt(post).isPresent())
                .collect(Collectors.toMap(Post::getSeriesId,
                        post -> lastChangedAt(post).orElseThrow(),
                        BinaryOperator.maxBy(Comparator.<LocalDateTime>naturalOrder())));

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 首页
        appendUrl(xml, siteUrl + "/", latestPostChange, "daily", "1.0");

        // 文章列表页
        appendUrl(xml, siteUrl + "/posts", latestPostChange, "daily", "0.9");

        // 专栏列表页
        appendUrl(xml, siteUrl + "/columns", latestPostChange, "weekly", "0.8");

        // 关于页面
        appendUrl(xml, siteUrl + "/about", Optional.empty(), "monthly", "0.5");

        // 各篇文章
        for (Post post : posts) {
            String slug = post.getSlug();
            if (slug == null || slug.isBlank()) continue;
            appendUrl(xml, siteUrl + "/posts/" + slug, lastChangedAt(post), "monthly", "0.8");
        }

        // 各个专栏详情页
        for (Series series : seriesList) {
            String slug = series.getSlug();
            if (slug == null || slug.isBlank()) continue;
            appendUrl(xml, siteUrl + "/columns/" + slug,
                    Optional.ofNullable(latestSeriesPostChanges.get(series.getId())), "weekly", "0.7");
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    private Optional<LocalDateTime> latestChange(List<Post> posts) {
        return posts.stream().map(this::lastChangedAt).flatMap(Optional::stream).max(Comparator.naturalOrder());
    }

    private Optional<LocalDateTime> lastChangedAt(Post post) {
        return Optional.ofNullable(post.getUpdatedAt()).or(() -> Optional.ofNullable(post.getPublishedAt()));
    }

    private void appendUrl(StringBuilder xml, String loc, Optional<LocalDateTime> lastmod,
                           String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        lastmod.ifPresent(value -> xml.append("    <lastmod>").append(value.format(ISO_DATE)).append("</lastmod>\n"));
        xml.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
        xml.append("    <priority>").append(priority).append("</priority>\n");
        xml.append("  </url>\n");
    }

    private String escapeXml(String value) {
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }
}
