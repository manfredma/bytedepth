package manfred.bytedepth.app.annotation;

import java.util.Set;

/** 批注高亮颜色白名单。 */
public final class AnnotationColor {

    public static final String RED = "red";
    public static final String YELLOW = "yellow";
    public static final String GREEN = "green";
    public static final String BLUE = "blue";

    private static final Set<String> SUPPORTED = Set.of(RED, YELLOW, GREEN, BLUE);

    private AnnotationColor() {
    }

    public static boolean isSupported(String color) {
        return color != null && SUPPORTED.contains(color);
    }
}
