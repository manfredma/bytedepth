package manfred.bytedepth.app.ops;

public record OpsRedisStatusDTO(boolean available, String usedMemoryHuman, long connectedClients,
                                long keyspaceHits, long keyspaceMisses, long postViewKeyCount,
                                long sessionKeyCount, String error) {

    private static final String UNAVAILABLE_ERROR = "Redis health check failed";

    public OpsRedisStatusDTO {
        error = available ? null : UNAVAILABLE_ERROR;
    }

    public OpsRedisStatusDTO(boolean available, String usedMemoryHuman, long connectedClients,
                             long keyspaceHits, long keyspaceMisses, long postViewKeyCount,
                             long sessionKeyCount) {
        this(available, usedMemoryHuman, connectedClients, keyspaceHits, keyspaceMisses,
                postViewKeyCount, sessionKeyCount, null);
    }

    public static OpsRedisStatusDTO unavailable() {
        return new OpsRedisStatusDTO(false, null, 0, 0, 0, 0, 0, UNAVAILABLE_ERROR);
    }
}
