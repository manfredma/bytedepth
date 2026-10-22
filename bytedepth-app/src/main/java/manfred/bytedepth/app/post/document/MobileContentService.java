package manfred.bytedepth.app.post.document;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;
import manfred.bytedepth.app.post.query.PublicPostAccess;
import manfred.bytedepth.app.series.GetSeriesPostsQryExe;
import manfred.bytedepth.app.series.SeriesNavigationQryExe;
import manfred.bytedepth.app.series.SeriesPostItemDTO;
import manfred.bytedepth.domain.category.CategoryRepository;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.document.ArticleDocument;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.owasp.html.Encoding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MobileContentService {
    private final PublicPostAccess access;
    private final ArticleRevisionService revisions;
    private final CategoryRepository categories;
    private final SeriesRepository series;
    private final GetSeriesPostsQryExe seriesPosts;
    private final SeriesNavigationQryExe navigation;
    private final URI siteOrigin;

    public MobileContentService(
            PublicPostAccess access,
            ArticleRevisionService revisions,
            CategoryRepository categories,
            SeriesRepository series,
            GetSeriesPostsQryExe seriesPosts,
            SeriesNavigationQryExe navigation,
            @Value("${bytedepth.site.url}") String siteUrl) {
        this.access = access;
        this.revisions = revisions;
        this.categories = categories;
        this.series = series;
        this.seriesPosts = seriesPosts;
        this.navigation = navigation;
        this.siteOrigin = URI.create(siteUrl.endsWith("/") ? siteUrl : siteUrl + "/");
    }

    public static final String SHELL_VERSION = "1";

    public PostState state(Long id) {
        try {
            return withContent(id, c -> new PostState(1, c.postId(), "AVAILABLE", c.identity()));
        } catch (PublicPostAccess.Unavailable unavailable) {
            return new PostState(1, id.toString(), "UNAVAILABLE", null);
        }
    }

    public PublicContent content(Long id) {
        return withContent(id, Function.identity());
    }
    /** Callback (including conditional response decisions) executes while the visibility lock is held. */
    public <T> T withContent(Long id, Function<PublicContent, T> action) {
        return access.withPublishedPost(id, post -> action.apply(represent(post)));
    }

    private PublicContent represent(Post post) {
        var revision = revisions.ensureCurrent(post);
        var rendered = revision.rendered();
        var category = post.getCategoryId() == null
                ? null
                : categories
                        .findById(post.getCategoryId())
                        .map(c -> new Category(c.getId().toString(), c.getName(), c.getSlug()))
                        .orElse(null);
        var column = post.getSeriesId() == null
                ? null
                : series.findById(post.getSeriesId())
                        .map(s -> {
                            var nav = navigation.execute(s.getId(), post.getId(), seriesPosts.execute(s.getId()));
                            return new Series(
                                    s.getId().toString(),
                                    s.getName(),
                                    s.getSlug(),
                                    nav.posts().stream()
                                            .map(MobileContentService::seriesPost)
                                            .toList(),
                                    nav.position(),
                                    nav.total(),
                                    nav.progressPercent(),
                                    seriesPost(nav.previous()),
                                    seriesPost(nav.next()));
                        })
                        .orElse(null);
        var resources = resources(rendered.html());
        var document = rendered.document();
        // Legacy published rows can lack publishedAt; creation is the existing business fallback.
        String date = utc(post.getPublishedAt() == null ? post.getCreatedAt() : post.getPublishedAt());
        String hash = sha256(pack(
                1,
                post.getId(),
                post.getContentVersion(),
                document.projectionVersion(),
                document.digest(),
                SHELL_VERSION,
                siteOrigin,
                post.getSlug(),
                post.getTitle(),
                date,
                category == null ? null : pack(category.id(), category.name(), category.slug()),
                seriesKey(column),
                rendered.html(),
                pack(
                        document.projectionVersion(),
                        document.digest(),
                        document.text(),
                        pack(document.segments().stream()
                                .map(segment -> pack(
                                        segment.id(),
                                        segment.blockId(),
                                        segment.kind(),
                                        segment.text(),
                                        segment.start(),
                                        segment.end()))
                                .toArray())),
                pack(resources.stream()
                        .map(r -> pack(r.key(), r.url(), r.role(), r.required(), r.mediaType(), r.sha256()))
                        .toArray())));
        return new PublicContent(
                1,
                post.getId().toString(),
                post.getContentVersion(),
                document.projectionVersion(),
                document.digest(),
                hash,
                SHELL_VERSION,
                post.getSlug(),
                post.getTitle(),
                date,
                category,
                column,
                rendered.html(),
                document,
                resources);
    }

    public static String utc(LocalDateTime value) {
        return value == null
                ? null
                : value.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toString();
    }

    private static SeriesPost seriesPost(SeriesPostItemDTO post) {
        return post == null
                ? null
                : new SeriesPost(
                        post.getId().toString(),
                        post.getSlug(),
                        post.getTitle(),
                        post.getSeriesOrder(),
                        post.getEstimatedReadingMinutes());
    }

    private static String seriesPostKey(SeriesPost post) {
        return post == null
                ? null
                : pack(post.id(), post.slug(), post.title(), post.seriesOrder(), post.estimatedReadingMinutes());
    }

    private static String seriesKey(Series series) {
        return series == null
                ? null
                : pack(
                        series.id(),
                        series.name(),
                        series.slug(),
                        pack(series.posts().stream()
                                .map(MobileContentService::seriesPostKey)
                                .toArray()),
                        series.position(),
                        series.total(),
                        series.progressPercent(),
                        seriesPostKey(series.previous()),
                        seriesPostKey(series.next()));
    }

    private static final Pattern IMAGE = Pattern.compile("<img\\b[^>]*?\\bsrc=\"([^\"]+)\"");

    // Enumerate sanitized authored sources only; no network access or claim about unknown external bytes.
    private List<Resource> resources(String html) {
        var sources = new TreeSet<String>();
        var matcher = IMAGE.matcher(html);
        while (matcher.find()) sources.add(resourceUrl(Encoding.decodeHtml(matcher.group(1), true)));
        return sources.stream()
                .map(url -> new Resource(sha256(url), url, "image", true, null, null))
                .toList();
    }

    private static final Pattern HTTP_SOURCE = Pattern.compile("(?i)^https?://");

    private String resourceUrl(String source) {
        try {
            return siteOrigin.resolve(URI.create(source)).normalize().toASCIIString();
        } catch (IllegalArgumentException invalidUri) {
            // URI is stricter than browser URL parsing (e.g. literal % or [] in a path).
            // Keep authored characters: encoding only this manifest would break reader src matching.
            // Even an unusable URL remains required, so failed images cannot mark a cache complete.
            if (HTTP_SOURCE.matcher(source).find()) return source;
            if (source.startsWith("//")) return siteOrigin.getScheme() + ":" + source;
            if (source.startsWith("/")) return siteOrigin.resolve("/").toASCIIString() + source.substring(1);
            return siteOrigin.toASCIIString() + source;
        }
    }

    // Fixed field ordering plus length-prefixed UTF-8 avoids ambiguous delimiter concatenations.
    private static String pack(Object... fields) {
        var result = new StringBuilder();
        for (Object field : fields) {
            if (field == null) result.append("-1:");
            else {
                var value = field.toString();
                result.append(value.getBytes(StandardCharsets.UTF_8).length)
                        .append(':')
                        .append(value);
            }
        }
        return result.toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot fingerprint mobile representation", exception);
        }
    }

    public record ContentIdentity(
            String postId,
            int contentVersion,
            int projectionVersion,
            String digest,
            String representationHash,
            String shellVersion) {}

    public record PostState(int schemaVersion, String postId, String availability, ContentIdentity identity) {}

    public record Category(String id, String name, String slug) {}

    public record SeriesPost(
            String id, String slug, String title, Integer seriesOrder, Integer estimatedReadingMinutes) {}

    public record Series(
            String id,
            String name,
            String slug,
            List<SeriesPost> posts,
            int position,
            int total,
            int progressPercent,
            SeriesPost previous,
            SeriesPost next) {}

    public record Resource(String key, String url, String role, boolean required, String mediaType, String sha256) {}

    public record PublicContent(
            int schemaVersion,
            String postId,
            int contentVersion,
            int projectionVersion,
            String digest,
            String representationHash,
            String shellVersion,
            String slug,
            String title,
            String publishedAt,
            Category category,
            Series series,
            String html,
            ArticleDocument document,
            List<Resource> resources) {
        public ContentIdentity identity() {
            return new ContentIdentity(
                    postId, contentVersion, projectionVersion, digest, representationHash, shellVersion);
        }
    }
}
