package manfred.bytedepth.adapter.web.filter;

import lombok.Getter;

/**
 * 过滤下拉选项。
 */
@Getter
public class FilterOption {

    private final String value;
    private final String label;
    private final boolean selected;

    private FilterOption(String value, String label, boolean selected) {
        this.value = value;
        this.label = label;
        this.selected = selected;
    }

    public static FilterOption of(String value, String label) {
        return new FilterOption(value, label, false);
    }

    public static FilterOption of(String value, String label, boolean selected) {
        return new FilterOption(value, label, selected);
    }
}
