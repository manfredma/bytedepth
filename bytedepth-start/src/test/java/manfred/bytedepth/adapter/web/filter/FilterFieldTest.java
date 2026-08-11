package manfred.bytedepth.adapter.web.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterFieldTest {

    @Test
    void textField() {
        FilterField f = FilterField.text("title", "标题", "Spring", "输入关键字");
        assertEquals("title", f.getName());
        assertEquals("标题", f.getLabel());
        assertEquals("TEXT", f.getType());
        assertEquals("Spring", f.getValue());
        assertEquals("输入关键字", f.getPlaceholder());
        assertNull(f.getOptions());
    }

    @Test
    void numberField() {
        FilterField f = FilterField.number("postId", "文章 ID", "12", "数字");
        assertEquals("NUMBER", f.getType());
        assertEquals("12", f.getValue());
    }

    @Test
    void selectFieldWithSelectedOption() {
        FilterField f = FilterField.select("status", "状态", "PUBLISHED",
                List.of(FilterOption.of("", "全部"), FilterOption.of("PUBLISHED", "已发布", true)));
        assertEquals("SELECT", f.getType());
        assertTrue(f.getOptions().get(1).isSelected());
        assertFalse(f.getOptions().get(0).isSelected());
        assertEquals("PUBLISHED", f.getValue());
    }

    @Test
    void selectFieldWithoutSelection() {
        FilterField f = FilterField.select("status", "状态", "",
                List.of(FilterOption.of("", "全部"), FilterOption.of("DRAFT", "草稿")));
        assertTrue(f.getOptions().get(0).isSelected() == false);
        assertEquals(2, f.getOptions().size());
    }
}
