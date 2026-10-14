package manfred.bytedepth.app.post;

import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

/** Shared article HTML policy: sanitization also changes whitespace used by annotation offsets. */
public final class MarkdownHtmlPolicy {

    private MarkdownHtmlPolicy() {}

    /**
     * {@link PolicyFactory#and(PolicyFactory)} intersects attribute policies for elements allowed by
     * both factories. Keeping the heading-id grant here, alongside the block element grant, prevents
     * {@code Sanitizers.BLOCKS} from stripping in-page anchors.
     */
    private static final PolicyFactory BLOCKS_WITH_HEADING_IDS = new HtmlPolicyBuilder()
            .allowCommonBlockElements()
            .allowAttributes("id")
            .onElements("h1", "h2", "h3", "h4", "h5", "h6")
            .toFactory();

    // Generated footnote ids are required in both directions. Keep the grant restricted to
    // footnote anchors/list items; original HTML remains escaped before the sanitizer sees it.
    private static final PolicyFactory FOOTNOTES = new HtmlPolicyBuilder()
            .allowElements("section", "sup", "a", "li")
            .allowAttributes("class")
            .matching(Pattern.compile("footnotes"))
            .onElements("section")
            .allowAttributes("class")
            .matching(Pattern.compile("footnote-ref"))
            .onElements("sup")
            .allowAttributes("class")
            .matching(Pattern.compile("footnote-backref"))
            .onElements("a")
            .allowAttributes("id")
            .matching(Pattern.compile("bd-fn(?:ref)?-(?:l[0-9a-f]+|i[1-9][0-9]*)(?:-[1-9][0-9]*)?"))
            .onElements("a", "li")
            .allowAttributes("aria-label")
            .matching(Pattern.compile("Back to reference [1-9][0-9]*(?:-[1-9][0-9]*)?"))
            .onElements("a")
            .toFactory();

    private static final PolicyFactory CONTENT_POLICY = BLOCKS_WITH_HEADING_IDS
            .and(FOOTNOTES)
            .and(Sanitizers.FORMATTING)
            .and(Sanitizers.LINKS)
            .and(Sanitizers.TABLES)
            .and(Sanitizers.IMAGES)
            .and(new HtmlPolicyBuilder()
                    .allowElements("pre", "code", "div", "span", "button")
                    .allowAttributes("class")
                    .matching(Pattern.compile("language-[A-Za-z0-9_-]{1,64}"))
                    .onElements("code")
                    .allowAttributes("class")
                    .matching(Pattern.compile(
                            "bd-code-(?:block(?:--collapsed|__(?:header|language|title|actions|copy|toggle|body|code|lines|line))?|tabs(?:__(?:list|tab|panels|panel)(?:--active)?)?)(?:"
                                    + " bd-code-(?:block(?:--collapsed|__(?:header|language|title|actions|copy|toggle|body|code|lines|line))?|tabs(?:__(?:list|tab|panels|panel)(?:--active)?)?))*|token(?:"
                                    + " [A-Za-z][A-Za-z0-9-]*)*"))
                    .onElements("div", "span", "button")
                    .allowAttributes("type")
                    .matching(Pattern.compile("button"))
                    .onElements("button")
                    .allowAttributes("role")
                    .matching(Pattern.compile("tablist|tab|tabpanel"))
                    .onElements("div", "button")
                    .allowAttributes("aria-label")
                    .matching(Pattern.compile("[^<>]{1,64}"))
                    .onElements("button")
                    .allowAttributes("aria-expanded", "aria-selected", "aria-hidden")
                    .matching(Pattern.compile("true|false"))
                    .onElements("div", "button")
                    .allowAttributes("hidden")
                    .matching(Pattern.compile("hidden"))
                    .onElements("div")
                    .allowAttributes("width")
                    .matching(Pattern.compile("(?:[1-9]\\d{2}|1\\d{3}|2[0-4]\\d{2})"))
                    .onElements("img")
                    .toFactory());

    public static String sanitize(String html) {
        return CONTENT_POLICY.sanitize(html);
    }
}
