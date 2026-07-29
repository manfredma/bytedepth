package manfred.bytedepth.app.post;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarkdownTextExtractorTest {

    @Test
    void extractsOnlyReaderVisibleProse() {
        String markdown = "# 标题\n\n> 一段值得阅读的导语，解释核心价值。\n\n```java\nint ignored = 1;\n```\n\n[查看详情](https://example.com)";

        assertEquals("一段值得阅读的导语，解释核心价值。 查看详情", MarkdownTextExtractor.plainText(markdown));
    }

    @Test
    void excerptPrefersFirstSubstantiveParagraphAndStopsAtSentence() {
        String markdown = "# 标题\n\n短句\n\n这里是一段足够长的引言，用来说明文章能带给读者什么帮助。这句不应出现在摘要中，因为首句已经完整。";

        assertEquals("这里是一段足够长的引言，用来说明文章能带给读者什么帮助。", MarkdownTextExtractor.excerpt(markdown, 30));
    }
}
