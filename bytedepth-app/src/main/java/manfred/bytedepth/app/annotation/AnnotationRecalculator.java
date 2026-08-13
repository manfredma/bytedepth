package manfred.bytedepth.app.annotation;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.DeltaType;
import com.github.difflib.patch.Patch;
import manfred.bytedepth.domain.annotation.PostAnnotation;

import java.util.ArrayList;
import java.util.List;

/**
 * 文章内容变更后，基于 Diff 信息重算所有批注的字符偏移。
 * <p>
 * 当文章内容被编辑时，已有批注的 startOffset/endOffset 会失效。
 * 本服务比较新旧内容，对每个字符位置计算偏移变化量，然后重算每个批注的新位置。
 * 批注范围内的文本在新内容中完全被删除时，该批注被标记为已删除（逻辑删除）。
 */
public class AnnotationRecalculator {

    /**
     * 重算批注偏移。
     *
     * @param oldContent 文章旧内容
     * @param newContent 文章新内容
     * @param annotations 该文所有批注（含已逻辑删除的）
     * @return 重算后的批注列表（偏移已调整，无法恢复的已标记 deleted=true）
     */
    public List<PostAnnotation> recalculate(String oldContent, String newContent, List<PostAnnotation> annotations) {
        if (annotations.isEmpty()) {
            return annotations;
        }
        if (oldContent.equals(newContent)) {
            return annotations;
        }

        // 1. 计算字符级 diff（逐字符拆分，diffInline 按连续字符块分组，不适合）
        List<String> oldChars = splitToChars(oldContent);
        List<String> newChars = splitToChars(newContent);
        Patch<String> patch = DiffUtils.diff(oldChars, newChars);

        // 2. 构建旧位置 → 新位置偏移映射表
        // 对于每个旧位置，计算其在新内容中的偏移
        int[] deltaMap = buildDeltaMap(oldContent, newContent, patch);

        // 3. 对每个批注重算偏移
        List<PostAnnotation> result = new ArrayList<>(annotations.size());
        for (PostAnnotation annotation : annotations) {
            result.add(recalculateAnnotation(annotation, deltaMap, oldContent, newContent));
        }
        return result;
    }

    /**
     * 构建偏移映射表：deltaMap[oldPos] = 从旧位置 oldPos 到新内容的偏移变化量。
     * 即新位置 = oldPos + deltaMap[oldPos]。
     * <p>
     * 如果 oldPos 处的字符在新内容中被删除，deltaMap[oldPos] = Integer.MIN_VALUE（标记为已删除）。
     * 映射表长度为 oldContent.length() + 1（多一个位置用于 endOffset 边界）。
     */
    static int[] buildDeltaMap(String oldContent, String newContent, Patch<String> patch) {
        int oldLen = oldContent.length();
        int[] deltaMap = new int[oldLen + 1];

        // 按 diff 块遍历，对每个旧位置计算累计偏移变化量
        int delta = 0;  // 累计偏移变化量
        int oldPos = 0; // 当前处理到的旧内容位置

        for (AbstractDelta<String> d : patch.getDeltas()) {
            int dOldPos = d.getSource().getPosition();
            int dOldSize = d.getSource().size();
            int dNewSize = d.getTarget().size();

            // 当前 delta 前的区间（不变）：delta 不变
            for (int i = oldPos; i < dOldPos && i <= oldLen; i++) {
                deltaMap[i] = delta;
            }

            if (d.getType() == DeltaType.EQUAL) {
                // 不变的字符：delta 不变，deltaMap 在该区间内保持 delta 值
                for (int i = dOldPos; i < dOldPos + dOldSize && i <= oldLen; i++) {
                    deltaMap[i] = delta;
                }
                oldPos = dOldPos + dOldSize;
            } else if (d.getType() == DeltaType.INSERT) {
                // 插入：不影响旧位置的偏移量，但 delta 累计值增加
                delta += dNewSize;
                oldPos = dOldPos;
            } else if (d.getType() == DeltaType.DELETE) {
                // 删除：被删除的字符标记为 MIN_VALUE，delta 累计值减少
                for (int i = dOldPos; i < dOldPos + dOldSize && i <= oldLen; i++) {
                    deltaMap[i] = Integer.MIN_VALUE;
                }
                delta -= dOldSize;
                oldPos = dOldPos + dOldSize;
            } else if (d.getType() == DeltaType.CHANGE) {
                // 修改 = 删除 + 插入：旧字符标记为 MIN_VALUE，delta 反映净变化
                for (int i = dOldPos; i < dOldPos + dOldSize && i <= oldLen; i++) {
                    deltaMap[i] = Integer.MIN_VALUE;
                }
                delta += (dNewSize - dOldSize);
                oldPos = dOldPos + dOldSize;
            }
        }

        // 剩余区间（delta 之后的所有位置）
        for (int i = oldPos; i <= oldLen; i++) {
            deltaMap[i] = delta;
        }

        return deltaMap;
    }

    /**
     * 重算单个批注的偏移。
     * <p>
     * 如果批注范围的字符全部被删除 → 标记为 deleted = true。
     * 否则更新 startOffset 和 endOffset。
     */
    static PostAnnotation recalculateAnnotation(PostAnnotation annotation, int[] deltaMap,
                                                String oldContent, String newContent) {
        if (annotation.deleted()) {
            return annotation;
        }

        int oldStart = annotation.startOffset();
        int oldEnd = annotation.endOffset();

        // 检查批注范围内是否所有字符都被删除（完全删除才标记为 deleted）
        boolean allDeleted = oldStart >= oldEnd;  // 空范围不算全删除
        if (oldStart >= 0 && oldStart < oldEnd) {
            allDeleted = true;
            for (int i = oldStart; i < oldEnd && i < deltaMap.length; i++) {
                if (deltaMap[i] != Integer.MIN_VALUE) {
                    allDeleted = false;
                    break;
                }
            }
        }
        boolean fullyDeleted = oldStart < oldEnd && oldStart >= 0 && allDeleted;

        if (fullyDeleted) {
            return new PostAnnotation(
                    annotation.id(), annotation.postId(), annotation.userId(),
                    annotation.ownerTokenHash(), annotation.selectedText(),
                    annotation.annotationText(), annotation.color(),
                    annotation.visibility(), annotation.startOffset(), annotation.endOffset(),
                    annotation.createdAt(), true);
        }

        // 计算新偏移：基于原始偏移和 delta 变化量
        // 对于被删除的位置（MIN_VALUE），safeDelta 返回 0（即该位置无偏移变化）
        int newStart = oldStart + safeDelta(deltaMap, oldStart);
        int newEnd = oldEnd + safeDelta(deltaMap, oldEnd);

        // 边界保护：新偏移不能超过新内容长度，不能为负
        newStart = Math.max(0, Math.min(newStart, newContent.length()));
        newEnd = Math.max(newStart, Math.min(newEnd, newContent.length()));

        if (newStart == oldStart && newEnd == oldEnd) {
            return annotation;  // 未变化，返回原对象
        }

        return new PostAnnotation(
                annotation.id(), annotation.postId(), annotation.userId(),
                annotation.ownerTokenHash(), annotation.selectedText(),
                annotation.annotationText(), annotation.color(),
                annotation.visibility(), newStart, newEnd,
                annotation.createdAt(), false);
    }

    /**
     * 将字符串拆分为单个字符的列表，用于字符级 diff 计算。
     */
    static List<String> splitToChars(String s) {
        List<String> chars = new ArrayList<>(s.length());
        for (int i = 0; i < s.length(); i++) {
            chars.add(String.valueOf(s.charAt(i)));
        }
        return chars;
    }

    private static int safeDelta(int[] deltaMap, int pos) {
        if (pos < 0) return 0;
        if (pos >= deltaMap.length) return deltaMap[deltaMap.length - 1];
        int d = deltaMap[pos];
        if (d == Integer.MIN_VALUE) return 0;
        return d;
    }
}