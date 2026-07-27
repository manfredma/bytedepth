package manfred.bytedepth.adapter.web.util;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class VisitRequestFilter {

    private final VisitFilterProperties properties;

    public boolean shouldRecord(Request request) {
        if (!properties.isEnabled()) {
            return true;
        }
        return properties.getRules().stream()
                .filter(VisitFilterProperties.Rule::isEnabled)
                .noneMatch(rule -> matches(rule, request.valueOf(rule.getField())));
    }

    private boolean matches(VisitFilterProperties.Rule rule, String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        String value = rule.getValue();
        if (rule.isIgnoreCase()) {
            source = source.toLowerCase(Locale.ROOT);
            value = value.toLowerCase(Locale.ROOT);
        }
        return switch (rule.getMatch()) {
            case EXACT -> source.equals(value);
            case PREFIX -> source.startsWith(value);
            case CONTAINS -> source.contains(value);
            case REGEX -> Pattern.compile(value, rule.isIgnoreCase() ? Pattern.CASE_INSENSITIVE : 0)
                    .matcher(source).find();
        };
    }

    public record Request(String userAgent) {
        String valueOf(VisitFilterProperties.Field field) {
            return switch (field) {
                case USER_AGENT -> userAgent;
            };
        }
    }
}
