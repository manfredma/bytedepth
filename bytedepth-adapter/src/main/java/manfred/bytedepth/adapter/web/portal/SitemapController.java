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
import java.util.List;

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
        String today = LocalDateTime.now().format(ISO_DATE);
        List<Post> posts = postRepository.findAllPublished();
        List<Series> seriesList = seriesRepository.findAll();

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // 首页
        appendUrl(xml, siteUrl + "/", today, "daily", "1.0");

        // 文章列表页
        appendUrl(xml, siteUrl + "/posts", today, "daily", "0.9");

        // 专栏列表页
        appendUrl(xml, siteUrl + "/columns", today, "weekly", "0.8");

        // 关于页面
        appendUrl(xml, siteUrl + "/about", today, "monthly", "0.5");

        // 各篇文章
        for (Post post : posts) {
            String slug = post.getSlug();
            if (slug == null || slug.isBlank()) continue;
            String lastmod = post.getUpdatedAt() != null
                    ? post.getUpdatedAt().format(ISO_DATE)
                    : post.getPublishedAt() != null
                            ? post.getPublishedAt().format(ISO_DATE)
                            : today;
            appendUrl(xml, siteUrl + "/posts/" + slug, lastmod, "monthly", "0.8");
        }

        // 各个专栏详情页
        for (Series series : seriesList) {
            String slug = series.getSlug();
            if (slug == null || slug.isBlank()) continue;
            appendUrl(xml, siteUrl + "/columns/" + slug, today, "weekly", "0.7");
        }

        xml.append("</urlset>");
        return xml.toString();
    }

    private void appendUrl(StringBuilder xml, String loc, String lastmod,
                           String changefreq, String priority) {
        xml.append("  <url>\n");
        xml.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
        xml.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
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
