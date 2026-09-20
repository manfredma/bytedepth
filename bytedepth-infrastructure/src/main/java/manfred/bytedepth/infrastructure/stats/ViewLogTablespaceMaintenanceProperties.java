package manfred.bytedepth.infrastructure.stats;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Objects;

@ConfigurationProperties(prefix = "bytedepth.analytics")
public record ViewLogTablespaceMaintenanceProperties(long optimizeMinDeletedRows,
                                                       long optimizeMinFreeBytes,
                                                       String optimizeLockName) {

    private static final String DEFAULT_LOCK_NAME = "bytedepth:view-log-tablespace";

    @ConstructorBinding
    public ViewLogTablespaceMaintenanceProperties {
        if (optimizeMinDeletedRows <= 0) {
            throw new IllegalArgumentException("optimizeMinDeletedRows must be positive");
        }
        if (optimizeMinFreeBytes < 0) {
            throw new IllegalArgumentException("optimizeMinFreeBytes must not be negative");
        }
        optimizeLockName = Objects.requireNonNullElse(optimizeLockName, DEFAULT_LOCK_NAME);
        if (optimizeLockName.isBlank()) {
            throw new IllegalArgumentException("optimizeLockName must not be blank");
        }
    }

    public ViewLogTablespaceMaintenanceProperties(long optimizeMinDeletedRows,
                                                   long optimizeMinFreeBytes) {
        this(optimizeMinDeletedRows, optimizeMinFreeBytes, DEFAULT_LOCK_NAME);
    }
}
