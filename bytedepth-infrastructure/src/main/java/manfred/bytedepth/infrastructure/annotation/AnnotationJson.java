package manfred.bytedepth.infrastructure.annotation;

import tools.jackson.databind.json.JsonMapper;

/** No polymorphic typing; only explicit internal value types are decoded. */
public final class AnnotationJson {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AnnotationJson() {}

    public static String write(Object value) {
        return JSON.writeValueAsString(value);
    }

    public static <T> T read(String value, Class<T> type) {
        return JSON.readValue(value, type);
    }
}
