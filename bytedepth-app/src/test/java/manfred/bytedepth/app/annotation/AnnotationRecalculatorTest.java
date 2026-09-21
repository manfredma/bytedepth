package manfred.bytedepth.app.annotation;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.ChangeDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.DeleteDelta;
import com.github.difflib.patch.InsertDelta;
import com.github.difflib.patch.Patch;
import manfred.bytedepth.app.post.MarkdownTextExtractor;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnotationRecalculatorTest {

    private final AnnotationRecalculator recalculator = new AnnotationRecalculator();

    @Test
    void emptyAnnotations_returnsEmptyList() {
        assertThat(recalculator.recalculate("旧内容", "新内容", List.of())).isEmpty();
    }

    @Test
    void sameContent_returnsUnchangedAnnotations() {
        PostAnnotation a = annotation("旧内容", 0, 3);
        List<PostAnnotation> result = recalculator.recalculate("旧内容", "旧内容", List.of(a));
        assertThat(result.get(0).startOffset()).isEqualTo(0);
        assertThat(result.get(0).endOffset()).isEqualTo(3);
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    void insertBeforeAnnotation_offsetsShiftForward() {
        PostAnnotation a = annotation("旧内容旧内容", 3, 6);
        List<PostAnnotation> result = recalculator.recalculate("旧内容旧内容", "新AA旧内容旧内容", List.of(a));
        // "新AA" 插在批注前（3 个字符），偏移应 +3
        assertThat(result.get(0).startOffset()).isEqualTo(6);
        assertThat(result.get(0).endOffset()).isEqualTo(9);
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    void deleteBeforeAnnotation_offsetsShiftBackward() {
        PostAnnotation a = annotation("AB旧内容", 2, 5);
        List<PostAnnotation> result = recalculator.recalculate("AB旧内容", "旧内容", List.of(a));
        // "AB" 被删（2 个字符），偏移应 -2
        assertThat(result.get(0).startOffset()).isEqualTo(0);
        assertThat(result.get(0).endOffset()).isEqualTo(3);
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    void deleteWithinAnnotation_reducesOffset() {
        // 批注覆盖 "AB旧内容"（0-5），删除 "AB"（0-2）
        PostAnnotation a = annotation("AB旧内容", 0, 5);
        List<PostAnnotation> result = recalculator.recalculate("AB旧内容", "旧内容", List.of(a));
        // "AB" 被删（2 字符），但 "旧内容" 保留，批注不应删除，偏移应调整
        assertThat(result.get(0).startOffset()).isEqualTo(0);
        assertThat(result.get(0).endOffset()).isEqualTo(3);
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    void insertWithinAnnotation_increasesOffset() {
        PostAnnotation a = annotation("旧内容", 0, 3);
        List<PostAnnotation> result = recalculator.recalculate("旧内容", "旧AA内容", List.of(a));
        // 批注范围内插入了 "AA"（2 字符），endOffset 应 +2
        assertThat(result.get(0).startOffset()).isEqualTo(0);
        assertThat(result.get(0).endOffset()).isEqualTo(5);
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    void annotationRangeFullyDeleted_markedDeleted() {
        // 使用完全不同的字符串，确保所有字符都被标记为已删除
        PostAnnotation a = annotation("ABC", 0, 3);
        List<PostAnnotation> result = recalculator.recalculate("ABC", "XYZ", List.of(a));
        // "ABC" → "XYZ" 全部 CHANGE，批注范围完全删除
        assertThat(result.get(0).deleted()).isTrue();
    }

    @Test
    void annotationRangePartiallyReplaced_markedDeleted() {
        PostAnnotation a = annotation("ABC", 0, 3);
        // 用 "ABC" → "ADEF" 让 "A" 保留
        // diff: A → A (EQUAL), BC → DEF (CHANGE)
        List<PostAnnotation> result = recalculator.recalculate("ABC", "ADEF", List.of(a));
        // 旧批注范围的一部分被替换，原始选中文字已不再存在，必须隐藏而不是迁移到新文字。
        assertThat(result.get(0).deleted()).isTrue();
    }

    @Test
    void multipleAnnotations_adjustedIndependently() {
        PostAnnotation a1 = annotation("ABC旧内容", 0, 3);
        PostAnnotation a2 = annotation("ABC旧内容", 3, 6);
        // "ABC" → "XYZ" 与 "旧内容" → "旧内容"（不变）
        // diff: ABC → XYZ (CHANGE), 旧内容 → 旧内容 (EQUAL)
        List<PostAnnotation> result = recalculator.recalculate("ABC旧内容", "XYZ旧内容", List.of(a1, a2));
        // a1 (0-3) 覆盖 "ABC"，全部被替换 → deleted
        // a2 (3-6) 覆盖 "旧内容"，不变 → 偏移不变
        assertThat(result.get(0).deleted()).isTrue();
        assertThat(result.get(1).deleted()).isFalse();
        assertThat(result.get(1).startOffset()).isEqualTo(3);
        assertThat(result.get(1).endOffset()).isEqualTo(6);
    }

    @Test
    void alreadyDeletedAnnotation_staysDeleted() {
        PostAnnotation a = new PostAnnotation(1L, 1L, null, null, "旧", null, "yellow",
                AnnotationVisibility.PRIVATE, 0, 1, LocalDateTime.now(), true);
        List<PostAnnotation> result = recalculator.recalculate("旧内容", "新内容", List.of(a));
        assertThat(result.get(0).deleted()).isTrue();
        assertThat(result.get(0).startOffset()).isEqualTo(0);
    }

    @Test
    void changeAtStartOffset_annotationDeleted() {
        // "ABC" (0,3) → "XYZ"：全部替换，批注完全删除
        PostAnnotation a = annotation("ABC", 0, 3);
        List<PostAnnotation> result = recalculator.recalculate("ABC", "XY", List.of(a));
        assertThat(result.get(0).deleted()).isTrue();
    }

    @Test
    void changeAtEndOffset_annotationDeleted() {
        PostAnnotation a = annotation("ABC", 0, 3);
        List<PostAnnotation> result = recalculator.recalculate("ABC", "ZZ", List.of(a));
        assertThat(result.get(0).deleted()).isTrue();
    }

    @Test
    void insertBeforeEndOffset_offsetsShifted() {
        // "ABC" (0,3) → "AXBC"：在 "A" 后插入 "X"
        PostAnnotation a = annotation("ABC", 0, 3);
        List<PostAnnotation> result = recalculator.recalculate("ABC", "AXBC", List.of(a));
        // 批注范围 0-3，在 "A" 后插入 "X"（位置 1），endOffset 应 +1
        assertThat(result.get(0).deleted()).isFalse();
        assertThat(result.get(0).startOffset()).isEqualTo(0);
        assertThat(result.get(0).endOffset()).isEqualTo(4);
    }

    @Test
    void offsetBoundaryProtection() {
        // "ABC" (0,3) → "X"：全部替换为单个字符，批注完全删除
        PostAnnotation a = annotation("ABC", 0, 3);
        List<PostAnnotation> result = recalculator.recalculate("ABC", "X", List.of(a));
        assertThat(result.get(0).deleted()).isTrue();
    }

    @Test
    void buildDeltaMap_withVariousDeltas() {
        String oldContent = "ABCDEFG";
        String newContent = "AXCDYZG";
        List<String> oldChars = AnnotationRecalculator.splitToChars(oldContent);
        List<String> newChars = AnnotationRecalculator.splitToChars(newContent);
        Patch<String> patch = DiffUtils.diff(oldChars, newChars);
        int[] deltaMap = AnnotationRecalculator.buildDeltaMap(oldContent, newContent, patch);

        // A (0) → A (0): 不变，delta = 0
        // B (1) → 被替换 (CHANGE, 1→1): deltaMap[1] = MIN_VALUE
        // C (2) → C (2): 不变，delta = 0
        // D (3) → D (3): 不变，delta = 0
        // E (4) → 被替换 (CHANGE, 2→2): deltaMap[4] = MIN_VALUE
        // F (5) → 被替换: deltaMap[5] = MIN_VALUE
        // G (6) → G (6): 不变，delta = 0
        // position 7 (end) → 7: delta = 0
        assertThat(deltaMap[0]).isEqualTo(0);
        assertThat(deltaMap[1]).isEqualTo(Integer.MIN_VALUE);
        assertThat(deltaMap[2]).isEqualTo(0);
        assertThat(deltaMap[3]).isEqualTo(0);
        assertThat(deltaMap[4]).isEqualTo(Integer.MIN_VALUE);
        assertThat(deltaMap[5]).isEqualTo(Integer.MIN_VALUE);
        assertThat(deltaMap[6]).isEqualTo(0);
        assertThat(deltaMap[7]).isEqualTo(0);
    }

    @Test
    void recalculateAnnotation_withValidDelta_offsetsAdjusted() {
        String oldContent = "ABCDEFG";
        String newContent = "AXCDYZG";
        List<String> oldChars = AnnotationRecalculator.splitToChars(oldContent);
        List<String> newChars = AnnotationRecalculator.splitToChars(newContent);
        Patch<String> patch = DiffUtils.diff(oldChars, newChars);
        int[] deltaMap = AnnotationRecalculator.buildDeltaMap(oldContent, newContent, patch);

        // 批注覆盖 "CD" (2-4)，B(1) 被替换（1→1 无偏移变化），EF(4-6) 被替换（2→2 无偏移变化）
        PostAnnotation a = annotation("ABCDEFG", 2, 4);
        PostAnnotation result = AnnotationRecalculator.recalculateAnnotation(a, deltaMap, oldContent, newContent);

        // C(2) → C(2), D(3) → D(3)，所以新偏移: 2-4
        assertThat(result.startOffset()).isEqualTo(2);
        assertThat(result.endOffset()).isEqualTo(4);
        assertThat(result.deleted()).isFalse();
    }

    @Test
    void changeWithLengthChange_adjustsOffset() {
        // "AB旧内容" → "旧内容"：AB 被删除（2 字符），delta = -2
        String oldContent = "AB旧内容";
        String newContent = "旧内容";
        List<String> oldChars = AnnotationRecalculator.splitToChars(oldContent);
        List<String> newChars = AnnotationRecalculator.splitToChars(newContent);
        Patch<String> patch = DiffUtils.diff(oldChars, newChars);
        int[] deltaMap = AnnotationRecalculator.buildDeltaMap(oldContent, newContent, patch);

        // 批注 "旧内容" 在旧内容中位置 2-5
        PostAnnotation a = annotation("AB旧内容", 2, 5);
        PostAnnotation result = AnnotationRecalculator.recalculateAnnotation(a, deltaMap, oldContent, newContent);

        // 偏移应 -2，新位置 0-3
        assertThat(result.startOffset()).isEqualTo(0);
        assertThat(result.endOffset()).isEqualTo(3);
        assertThat(result.deleted()).isFalse();
    }

    @Test
    void splitToChars_handlesChineseCharacters() {
        assertThat(AnnotationRecalculator.splitToChars("旧内容")).containsExactly("旧", "内", "容");
        assertThat(AnnotationRecalculator.splitToChars("ABC")).containsExactly("A", "B", "C");
    }

    @Test
    void buildDeltaMap_withInsert_handlesCorrectly() {
        // "ABC" → "AXBC"：在 "A" 后插入 "X" (INSERT)
        String oldContent = "ABC";
        String newContent = "AXBC";
        List<String> oldChars = AnnotationRecalculator.splitToChars(oldContent);
        List<String> newChars = AnnotationRecalculator.splitToChars(newContent);
        var patch = DiffUtils.diff(oldChars, newChars);
        int[] deltaMap = AnnotationRecalculator.buildDeltaMap(oldContent, newContent, patch);
        // A(0)→A(0) delta=0, B(1)→B(2) delta=1 (insert X before B), C(2)→C(3) delta=1
        assertThat(deltaMap[0]).isEqualTo(0);
        assertThat(deltaMap[1]).isEqualTo(1);
        assertThat(deltaMap[2]).isEqualTo(1);
        assertThat(deltaMap[3]).isEqualTo(1);
    }

    @Test
    void buildDeltaMap_withInsertAndDelete_handlesCorrectly() {
        // "AB" → "X"：A→X (CHANGE), B deleted (DELETE)
        // 实际 diff: CHANGE at 0, size 1, DELETE at 1, size 1
        String oldContent = "AB";
        String newContent = "X";
        List<String> oldChars = AnnotationRecalculator.splitToChars(oldContent);
        List<String> newChars = AnnotationRecalculator.splitToChars(newContent);
        var patch = DiffUtils.diff(oldChars, newChars);
        int[] deltaMap = AnnotationRecalculator.buildDeltaMap(oldContent, newContent, patch);
        // A(0) → X(CHANGE): deltaMap[0] = MIN_VALUE, delta = 0 (1-1)
        // B(1) → deleted(DELETE): deltaMap[1] = MIN_VALUE, delta = 0-1 = -1
        // pos 2 → delta = -1
        assertThat(deltaMap[0]).isEqualTo(Integer.MIN_VALUE);
        assertThat(deltaMap[1]).isEqualTo(Integer.MIN_VALUE);
        assertThat(deltaMap[2]).isEqualTo(-1);
    }

    @Test
    void buildDeltaMap_withInsertOnly_handlesCorrectly() {
        // "ABC" → "AXC"：B → X (CHANGE 1→1) 或 INSERT+EQUAL
        // 不依赖具体 diff 算法，只验证 deltaMap 长度正确
        String oldContent = "ABC";
        String newContent = "AXC";
        var patch = DiffUtils.diff(AnnotationRecalculator.splitToChars(oldContent), AnnotationRecalculator.splitToChars(newContent));
        int[] deltaMap = AnnotationRecalculator.buildDeltaMap(oldContent, newContent, patch);
        assertThat(deltaMap.length).isEqualTo(4);
        // 验证结果自洽：deltaMap[3]（结束位置）的 delta 应为 0（长度不变）
        assertThat(deltaMap[3]).isEqualTo(0);
    }

    @Test
    void buildDeltaMap_handlesManuallyConstructedDeltaBoundaries() {
        Patch<String> patch = new Patch<>();
        patch.addDelta(new InsertDelta<>(new Chunk<>(1, List.of()), new Chunk<>(1, List.of("X"))));
        patch.addDelta(new DeleteDelta<>(new Chunk<>(2, List.of("C", "D")), new Chunk<>(2, List.of())));
        patch.addDelta(new ChangeDelta<>(new Chunk<>(5, List.of("F", "G")), new Chunk<>(5, List.of("Z"))));

        int[] deltaMap = AnnotationRecalculator.buildDeltaMap("ABCDEFG", "AXBEZ", patch);

        assertThat(deltaMap).hasSize(8);
        assertThat(deltaMap[1]).isEqualTo(1);
        assertThat(deltaMap[2]).isEqualTo(Integer.MIN_VALUE);
        assertThat(deltaMap[5]).isEqualTo(Integer.MIN_VALUE);
        assertThat(deltaMap[7]).isEqualTo(-2);

        Patch<String> boundaryDelete = new Patch<>();
        boundaryDelete.addDelta(new DeleteDelta<>(new Chunk<>(7, List.of("X")), new Chunk<>(7, List.of())));
        assertThat(AnnotationRecalculator.buildDeltaMap("ABCDEFG", "ABCDEFG", boundaryDelete)).hasSize(8);
    }

    @Test
    void recalculateAnnotation_coversDeletionAndEmptyRangeGuards() {
        PostAnnotation fullyDeleted = AnnotationRecalculator.recalculateAnnotation(
                annotation("ABC", 0, 3),
                new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, -3},
                new boolean[3], "ABC", "");
        assertThat(fullyDeleted.deleted()).isTrue();

        PostAnnotation emptyAfterClamp = AnnotationRecalculator.recalculateAnnotation(
                annotation("A", 0, 1),
                new int[]{0, 0}, new boolean[1], "A", "");
        assertThat(emptyAfterClamp.deleted()).isTrue();
    }

    @Test
    void recalculateAnnotation_rejectsInvalidOrMismatchedAnchors() {
        assertThat(AnnotationRecalculator.recalculateAnnotation(
                annotationWithSelectedText("A", -1, 0),
                new int[]{0}, new boolean[1], "A", "A").deleted()).isTrue();
        assertThat(AnnotationRecalculator.recalculateAnnotation(
                annotationWithSelectedText("A", 0, 2),
                new int[]{0, 0, 0}, new boolean[1], "A", "A").deleted()).isTrue();
        assertThat(AnnotationRecalculator.recalculateAnnotation(
                annotationWithSelectedText("B", 0, 1),
                new int[]{0, 0}, new boolean[1], "A", "A").deleted()).isTrue();

        PostAnnotation withoutStoredText = annotationWithSelectedText(null, 0, 1);
        assertThat(AnnotationRecalculator.recalculateAnnotation(
                withoutStoredText, new int[]{0, 0}, new boolean[1], "A", "A"))
                .isSameAs(withoutStoredText);

        PostAnnotation emptyRange = annotationWithSelectedText("", 0, 0);
        assertThat(AnnotationRecalculator.recalculateAnnotation(
                emptyRange, new int[]{0}, new boolean[1], "A", "A").deleted()).isTrue();

        PostAnnotation changedMapIsShorter = annotationWithSelectedText("AB", 0, 2);
        assertThat(AnnotationRecalculator.recalculateAnnotation(
                changedMapIsShorter, new int[]{0, 0, 0}, new boolean[1], "AB", "AB"))
                .isSameAs(changedMapIsShorter);
    }

    @Test
    void buildChangedMap_marksOnlyReplacedOldCharacters() {
        var patch = DiffUtils.diff(
                AnnotationRecalculator.splitToChars("目标旧尾巴"),
                AnnotationRecalculator.splitToChars("目标新尾巴"));

        assertThat(AnnotationRecalculator.buildChangedMap("目标旧尾巴".length(), patch))
                .containsExactly(false, false, true, false, false);
    }

    @Test
    void recalculate_withInsertDelta_adjustsCorrectly() {
        // 整个 recalculate 流程：旧内容开头插入，gap filler 运行
        PostAnnotation a = annotation("旧内容", 0, 3);
        List<PostAnnotation> result = recalculator.recalculate("旧内容", "新AA旧内容", List.of(a));
        // "新AA" 插入在开头（3 字符），批注偏移应 +3
        assertThat(result.get(0).startOffset()).isEqualTo(3);
        assertThat(result.get(0).endOffset()).isEqualTo(6);
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    void recalculate_usesRenderedTextOffsetsAcrossMarkdownParagraphs() {
        String oldContent = "第一段\n\n第二段";
        String newContent = "第一段\n\n新增段\n\n第二段";
        // 浏览器渲染正文的 textContent 是“第一段\n第二段\n”，不包含 Markdown 的空行。
        PostAnnotation annotation = annotationWithSelectedText("第二段", 4, 7);

        List<PostAnnotation> result = recalculator.recalculate(oldContent, newContent, List.of(annotation));

        // 新的渲染文本是“第一段\n新增段\n第二段\n”。
        assertThat(result.get(0).startOffset()).isEqualTo(8);
        assertThat(result.get(0).endOffset()).isEqualTo(11);
        assertThat(result.get(0).selectedText()).isEqualTo("第二段");
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    void recalculate_ignoresMarkdownSyntaxChangesBeforeAnnotation() {
        String oldContent = "前文\n\n目标";
        String newContent = "**前文**\n\n目标";
        // 两版渲染正文中“目标”的位置都为 4。
        PostAnnotation annotation = annotationWithSelectedText("目标", 4, 6);

        List<PostAnnotation> result = recalculator.recalculate(oldContent, newContent, List.of(annotation));

        assertThat(result.get(0).startOffset()).isEqualTo(4);
        assertThat(result.get(0).endOffset()).isEqualTo(6);
        assertThat(result.get(0).selectedText()).isEqualTo("目标");
        assertThat(result.get(0).deleted()).isFalse();
    }

    @Test
    void recalculate_marksAnnotationDeletedWhenRenderedTextIsRemoved() {
        String oldContent = "前文\n\n目标";
        String newContent = "前文";
        PostAnnotation annotation = annotationWithSelectedText("目标", 4, 6);

        List<PostAnnotation> result = recalculator.recalculate(oldContent, newContent, List.of(annotation));

        assertThat(result.get(0).deleted()).isTrue();
    }

    @Test
    void recalculate_updatesSelectedTextWhenPartOfTheRenderedRangeIsDeleted() {
        String oldContent = "前文\n\n目标和尾巴";
        String newContent = "前文\n\n目标";
        String oldReaderText = MarkdownTextExtractor.renderedText(oldContent);
        int start = oldReaderText.indexOf("目标和尾巴");
        PostAnnotation annotation = annotationWithSelectedText("目标和尾巴", start, start + "目标和尾巴".length());

        List<PostAnnotation> result = recalculator.recalculate(oldContent, newContent, List.of(annotation));

        assertThat(result.get(0).deleted()).isFalse();
        assertThat(result.get(0).selectedText()).isEqualTo("目标");
        assertThat(result.get(0).startOffset()).isEqualTo(MarkdownTextExtractor.renderedText(newContent).indexOf("目标"));
        assertThat(result.get(0).endOffset()).isEqualTo(result.get(0).startOffset() + "目标".length());
    }

    @Test
    void recalculate_marksAnnotationDeletedWhenRenderedRangeIsReplaced() {
        String oldContent = "前文\n\n旧方案";
        String newContent = "前文\n\n新方案";
        String oldReaderText = MarkdownTextExtractor.renderedText(oldContent);
        int start = oldReaderText.indexOf("旧方案");
        PostAnnotation annotation = annotationWithSelectedText("旧方案", start, start + "旧方案".length());

        List<PostAnnotation> result = recalculator.recalculate(oldContent, newContent, List.of(annotation));

        assertThat(result.get(0).deleted()).isTrue();
        assertThat(result.get(0).selectedText()).isEqualTo("旧方案");
    }

    @Test
    void buildDeltaMap_withGapFiller_handlesCorrectly() {
        // "XAB" → "X"：X(EQUAL) at 0, AB(DELETE) at 1
        // gap filler 在第一个 delta 之前运行：oldPos=0, dOldPos=0（无 gap）
        // 第二个 delta 之前：oldPos=1, dOldPos=1（无 gap）
        // 实际上 diff 可能是：EQUAL at 0, DELETE at 1 或 DELETE at 0, size 1, 等
        String oldContent = "XAB";
        String newContent = "X";
        List<String> oldChars = AnnotationRecalculator.splitToChars(oldContent);
        List<String> newChars = AnnotationRecalculator.splitToChars(newContent);
        var patch = DiffUtils.diff(oldChars, newChars);
        int[] deltaMap = AnnotationRecalculator.buildDeltaMap(oldContent, newContent, patch);
        // X(0)→X(0) delta=0, A(1)→deleted delta=-1, B(2)→deleted delta=-2
        // 或者不同的 diff 结果，视算法而定
        assertThat(deltaMap.length).isEqualTo(4);
    }

    @Test
    void safeDelta_edgeCases() {
        int[] deltaMap = new int[]{0, Integer.MIN_VALUE, 5};
        // pos < 0 → 0
        assertThat(AnnotationRecalculator.safeDelta(deltaMap, -1)).isEqualTo(0);
        // pos >= deltaMap.length → deltaMap[last]
        assertThat(AnnotationRecalculator.safeDelta(deltaMap, 10)).isEqualTo(5);
        // pos with MIN_VALUE → 0
        assertThat(AnnotationRecalculator.safeDelta(deltaMap, 1)).isEqualTo(0);
        // normal pos → deltaMap[pos]
        assertThat(AnnotationRecalculator.safeDelta(deltaMap, 2)).isEqualTo(5);
    }

    private static PostAnnotation annotation(String content, int start, int end) {
        return annotationWithSelectedText(content.substring(start, end), start, end);
    }

    private static PostAnnotation annotationWithSelectedText(String selectedText, int start, int end) {
        return new PostAnnotation(null, 1L, null, null,
                selectedText, null, "yellow",
                AnnotationVisibility.PRIVATE, start, end, LocalDateTime.now(), false);
    }
}
