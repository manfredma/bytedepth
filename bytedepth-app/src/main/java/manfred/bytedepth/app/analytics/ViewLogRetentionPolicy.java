package manfred.bytedepth.app.analytics;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public final class ViewLogRetentionPolicy {

    private final int retentionDays;
    private final ZoneId zone;

    public ViewLogRetentionPolicy(int retentionDays, ZoneId zone) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retentionDays must be positive");
        }
        this.retentionDays = retentionDays;
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    public LocalDateTime retentionCutoff(LocalDateTime now) {
        return shifted(now).truncatedTo(ChronoUnit.HOURS);
    }

    public LocalDateTime detailCutoff(LocalDateTime now) {
        return shifted(now);
    }

    public LocalDateTime bucketStart(LocalDateTime value) {
        return Objects.requireNonNull(value, "value").truncatedTo(ChronoUnit.HOURS);
    }

    private LocalDateTime shifted(LocalDateTime now) {
        return ZonedDateTime.of(Objects.requireNonNull(now, "now"), zone)
                .minusDays(retentionDays)
                .toLocalDateTime();
    }
}
