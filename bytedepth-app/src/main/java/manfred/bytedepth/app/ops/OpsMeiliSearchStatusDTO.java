package manfred.bytedepth.app.ops;

import java.util.Map;

public record OpsMeiliSearchStatusDTO(boolean healthAvailable, boolean statsAvailable,
                                      Map<String, Object> stats) {

    public OpsMeiliSearchStatusDTO {
        stats = stats == null ? Map.of() : Map.copyOf(stats);
    }

    public static OpsMeiliSearchStatusDTO unavailable() {
        return new OpsMeiliSearchStatusDTO(false, false, Map.of());
    }
}
