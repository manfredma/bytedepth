package manfred.bytedepth.app.ops;

/** MeiliSearch health only; raw service responses must not reach the operations API. */
public record OpsMeiliSearchStatusDTO(boolean healthAvailable, boolean statsAvailable) {

    public static OpsMeiliSearchStatusDTO unavailable() {
        return new OpsMeiliSearchStatusDTO(false, false);
    }
}
