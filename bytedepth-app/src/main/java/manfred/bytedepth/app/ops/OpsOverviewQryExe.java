package manfred.bytedepth.app.ops;

import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.time.Instant;

@Component
public class OpsOverviewQryExe {

    private final OpsDatabasePort databasePort;
    private final OpsRedisPort redisPort;
    private final OpsMeiliSearchPort meiliSearchPort;

    public OpsOverviewQryExe(OpsDatabasePort databasePort, OpsRedisPort redisPort,
                             OpsMeiliSearchPort meiliSearchPort) {
        this.databasePort = databasePort;
        this.redisPort = redisPort;
        this.meiliSearchPort = meiliSearchPort;
    }

    /**
     * Each external service is queried independently so one unavailable service cannot prevent
     * the operations page from showing the remaining service states.
     */
    public OpsOverviewDTO execute() {
        long startedAt = ManagementFactory.getRuntimeMXBean().getStartTime();
        return new OpsOverviewDTO(
                Instant.ofEpochMilli(startedAt),
                Math.max(0, System.currentTimeMillis() - startedAt),
                inspectDatabase(),
                inspectRedis(),
                inspectMeiliSearch());
    }

    private OpsDatabaseStatusDTO inspectDatabase() {
        try {
            return databasePort.inspect();
        } catch (RuntimeException e) {
            return OpsDatabaseStatusDTO.unavailable();
        }
    }

    private OpsRedisStatusDTO inspectRedis() {
        try {
            return redisPort.inspect();
        } catch (RuntimeException e) {
            return OpsRedisStatusDTO.unavailable();
        }
    }

    private OpsMeiliSearchStatusDTO inspectMeiliSearch() {
        try {
            return meiliSearchPort.inspect();
        } catch (RuntimeException e) {
            return OpsMeiliSearchStatusDTO.unavailable();
        }
    }
}
