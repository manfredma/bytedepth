package manfred.bytedepth.app.ops;

import java.time.Instant;

public record OpsOverviewDTO(Instant jvmStartedAt, long uptimeMillis,
                             OpsDatabaseStatusDTO database,
                             OpsRedisStatusDTO redis,
                             OpsMeiliSearchStatusDTO meiliSearch) {
}
