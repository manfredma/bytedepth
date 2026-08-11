package manfred.bytedepth.adapter.web.filter;

import lombok.Getter;

import java.util.List;

/**
 * 统一查询过滤组件 {@code bd-filter-bar} 的字段配置。
 * <p>
 * Controller 为每个后台列表页构建一个 {@link List}，交由
 * {@code templates/fragments/filter-bar.html} 渲染。字段类型由
 * {@link #getType()} 决定：TEXT / NUMBER / SELECT。
 */
@Getter
public class FilterField {

    private final String name;
    private final String label;
    private final String type;
    private final String value;
    private final String placeholder;
    private final List<FilterOption> options;

    private FilterField(String name, String label, String type, String value,
                        String placeholder, List<FilterOption> options) {
        this.name = name;
        this.label = label;
        this.type = type;
        this.value = value;
        this.placeholder = placeholder;
        this.options = options;
    }

    public static FilterField text(String name, String label, String value, String placeholder) {
        return new FilterField(name, label, "TEXT", value, placeholder, null);
    }

    public static FilterField number(String name, String label, String value, String placeholder) {
        return new FilterField(name, label, "NUMBER", value, placeholder, null);
    }

    public static FilterField select(String name, String label, String value, List<FilterOption> options) {
        return new FilterField(name, label, "SELECT", value, null, options);
    }
}
