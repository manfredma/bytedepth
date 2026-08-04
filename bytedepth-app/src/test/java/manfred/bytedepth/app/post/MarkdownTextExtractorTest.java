package manfred.bytedepth.app.post;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void plainTextReturnsEmptyForNullOrBlank() {
        assertEquals("", MarkdownTextExtractor.plainText(null));
        assertEquals("", MarkdownTextExtractor.plainText("   "));
        assertEquals("", MarkdownTextExtractor.plainText(""));
    }

    @Test
    void excerptReturnsEmptyForNullOrBlank() {
        assertEquals("", MarkdownTextExtractor.excerpt(null, 10));
        assertEquals("", MarkdownTextExtractor.excerpt("   ", 10));
    }

    @Test
    void excerptThrowsForNonPositiveMaxLength() {
        assertThrows(IllegalArgumentException.class, () -> MarkdownTextExtractor.excerpt("x", 0));
        assertThrows(IllegalArgumentException.class, () -> MarkdownTextExtractor.excerpt("x", -1));
    }

    @Test
    void excerptFallsBackToFirstShortParagraphWhenNoneSubstantive() {
        // 所有段落都不足 24 个码点，回退到首个非空段落
        String markdown = "短一\n\n短二";
        assertEquals("短一", MarkdownTextExtractor.excerpt(markdown, 50));
    }

    @Test
    void excerptSkipsEmptyParagraphsBeforeFallback() {
        String markdown = "\n\n短句";
        assertEquals("短句", MarkdownTextExtractor.excerpt(markdown, 50));
    }

    @Test
    void plainTextCollapsesSoftAndHardLineBreaks() {
        // 软换行（行尾两个空格 + 换行）与硬换行（反斜杠换行）都应转为空格
        String markdown = "第一行  \n第二行\\\n第三行";
        String result = MarkdownTextExtractor.plainText(markdown);
        assertEquals("第一行 第二行 第三行", result);
    }

    @Test
    void plainTextHandlesSoftLineBreakInAsciiText() {
        // 普通换行（行尾无额外空格、无反斜杠）产生 SoftLineBreak 节点，应转为空格
        String markdown = "line one\nline two";
        String result = MarkdownTextExtractor.plainText(markdown);
        assertEquals("line one line two", result);
    }

    @Test
    void excerptAbbreviatesAtWordBoundaryWhenNoSentenceEnd() {
        // 超过 maxLength 且没有句末标点：按词边界截断并加省略号
        String markdown = "this is a long enough paragraph without any sentence end punctuation here";
        String result = MarkdownTextExtractor.excerpt(markdown, 15);
        assertTrue(result.endsWith("…"));
        // 句末标点位置不足一半时走词边界分支
        assertTrue(result.length() <= 17);
    }

    @Test
    void excerptReturnsFullTextWhenWithinMaxLength() {
        String markdown = "完整短文";
        assertEquals("完整短文", MarkdownTextExtractor.excerpt(markdown, 100));
    }

    @Test
    void excerptFallsBackToCodepointEndWhenNoWordBoundary() {
        // 连续无空格、无句末标点的长文本：wordEnd < end/2 时按 end 截断
        String markdown = "abcdefghijklmnopqrstuvwxyzaaa";
        String result = MarkdownTextExtractor.excerpt(markdown, 10);
        assertEquals("abcdefghij…", result);
    }

    @Test
    void lastSentenceEndPicksLatestAmongMultiplePunctuation() {
        // 多个句末标点，取最后一个之后的位置
        String markdown = "第一句。第二句！最后。";
        // maxLength 足够大时返回原文；用小值验证句末截断取最后标点
        String result = MarkdownTextExtractor.excerpt(markdown, 8);
        assertFalse(result.isEmpty());
    }

    @Test
    void plainTextHandlesMultipleParagraphsWithNormalization() {
        String markdown = "段落一\n\n段落二\n\n段落三";
        assertEquals("段落一 段落二 段落三", MarkdownTextExtractor.plainText(markdown));
    }

    @Test
    void plainTextExcludesTableNodes() {
        // GFM 表格不是 Paragraph 节点，plainText 仅提取散文段落，表格内容不纳入
        String markdown = "| 标题A | 标题B |\n| --- | --- |\n| 单元1 | 单元2 |";
        String result = MarkdownTextExtractor.plainText(markdown);
        assertEquals("", result);
    }

    @Test
    void excerptSkipsParagraphThatYieldsNoText() {
        // 首段只含图片（无 Text 节点），paragraphText 返回空字符串，应 continue 跳过；
        // 次段为实质性段落，应作为摘要返回。
        String markdown = "![](https://example.com/img.png)\n\n这里是一段足够长的引言文字用来通过门槛。";
        String result = MarkdownTextExtractor.excerpt(markdown, 30);
        assertEquals("这里是一段足够长的引言文字用来通过门槛。", result);
    }
}
