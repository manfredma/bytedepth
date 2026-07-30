package manfred.bytedepth.app.ops;

public record OpsRedisStatusDTO(boolean available, String usedMemoryHuman, long connectedClients,
                                long keyspaceHits, long keyspaceMisses, long postViewKeyCount,
                                long sessionKeyCount) {

    public static OpsRedisStatusDTO unavailable() {
        return new OpsRedisStatusDTO(false, null, 0, 0, 0, 0, 0);
    }
}
