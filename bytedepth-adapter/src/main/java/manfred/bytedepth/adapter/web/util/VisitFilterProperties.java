package manfred.bytedepth.adapter.web.util;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bytedepth.visit-filter")
public class VisitFilterProperties {

    private boolean enabled = true;
    private List<Rule> rules = new ArrayList<>();

    @PostConstruct
    void validate() {
        Set<String> ids = new HashSet<>();
        for (Rule rule : rules) {
            if (rule.id == null || rule.id.isBlank()) {
                throw new IllegalStateException("访问过滤规则必须配置 id");
            }
            if (!ids.add(rule.id)) {
                throw new IllegalStateException("访问过滤规则 id 重复: " + rule.id);
            }
            if (rule.field == null || rule.match == null || rule.value == null || rule.value.isBlank()) {
                throw new IllegalStateException("访问过滤规则配置不完整: " + rule.id);
            }
            if (rule.match == Match.REGEX) {
                try {
                    Pattern.compile(rule.value);
                } catch (PatternSyntaxException exception) {
                    throw new IllegalStateException("访问过滤规则正则表达式非法: " + rule.id, exception);
                }
            }
        }
    }

    @Getter
    @Setter
    public static class Rule {
        private String id;
        private Field field;
        private Match match;
        private String value;
        private boolean ignoreCase = true;
        private boolean enabled = true;
    }

    public enum Field {
        USER_AGENT
    }

    public enum Match {
        EXACT, PREFIX, CONTAINS, REGEX
    }
}
