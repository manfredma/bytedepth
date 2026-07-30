package manfred.bytedepth.app.ops;

/** MeiliSearch health only; raw service responses must not reach the operations API. */
public record OpsMeiliSearchStatusDTO(boolean healthAvailable, boolean statsAvailable, String error) {

    private static final String UNAVAILABLE_ERROR = "MeiliSearch health check failed";

    public OpsMeiliSearchStatusDTO {
        error = healthAvailable && statsAvailable ? null : UNAVAILABLE_ERROR;
    }

    public OpsMeiliSearchStatusDTO(boolean healthAvailable, boolean statsAvailable) {
        this(healthAvailable, statsAvailable, null);
    }

    public static OpsMeiliSearchStatusDTO unavailable() {
        return new OpsMeiliSearchStatusDTO(false, false, UNAVAILABLE_ERROR);
    }
}
