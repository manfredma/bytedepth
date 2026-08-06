package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** RSS 2.0 最近文章 feed，供订阅器和搜索引擎发现新内容。 */
@Controller
@RequiredArgsConstructor
public class FeedController {

    private static final int SUMMARY_LENGTH = 300;

    @Value("${bytedepth.site.url}")
    private String siteUrl;

    private final PostRepository postRepository;

    @GetMapping(value = "/feed.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @ResponseBody
    public String feed() {
        List<Post> posts = postRepository.findAllPublished().stream().limit(20).toList();
        String feedUrl = siteUrl + "/feed.xml";
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">\n<channel>\n")
                .append("<title>bytedepth</title>\n<link>").append(escapeXml(siteUrl)).append("</link>\n")
                .append("<description>bytedepth 最近发布的文章</description>\n")
                .append("<atom:link href=\"").append(escapeXml(feedUrl))
                .append("\" rel=\"self\" type=\"application/rss+xml\"/>\n");
        latestChange(posts).ifPresent(value -> xml.append("<lastBuildDate>").append(rfc1123(value)).append("</lastBuildDate>\n"));
        for (Post post : posts) {
            appendItem(xml, post);
        }
        return xml.append("</channel>\n</rss>").toString();
    }

    private void appendItem(StringBuilder xml, Post post) {
        String url = siteUrl + "/posts/" + post.getSlug();
        xml.append("<item>\n<title>").append(escapeXml(post.getTitle())).append("</title>\n<link>")
                .append(escapeXml(url)).append("</link>\n<guid isPermaLink=\"true\">")
                .append(escapeXml(url)).append("</guid>\n<description>").append(escapeXml(summary(post.getContent())))
                .append("</description>\n");
        changedAt(post).ifPresent(value -> xml.append("<pubDate>").append(rfc1123(value)).append("</pubDate>\n"));
        xml.append("</item>\n");
    }

    private Optional<LocalDateTime> latestChange(List<Post> posts) {
        return posts.stream().map(this::changedAt).flatMap(Optional::stream).max(Comparator.naturalOrder());
    }

    private Optional<LocalDateTime> changedAt(Post post) {
        return Optional.ofNullable(post.getPublishedAt()).or(() -> Optional.ofNullable(post.getUpdatedAt()));
    }

    private String summary(String content) {
        String normalized = Optional.ofNullable(content).orElse("").replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), SUMMARY_LENGTH));
    }

    private String rfc1123(LocalDateTime value) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(value.atOffset(ZoneOffset.ofHours(8)));
    }

    private String escapeXml(String value) {
        return Optional.ofNullable(value).orElse("").replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
