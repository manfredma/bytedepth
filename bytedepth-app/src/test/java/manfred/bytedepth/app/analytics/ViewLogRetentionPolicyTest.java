package manfred.bytedepth.app.analytics;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViewLogRetentionPolicyTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    void retentionCutoffRoundsDownToTheCompletedHour() {
        var policy = new ViewLogRetentionPolicy(7, SHANGHAI);

        assertEquals(LocalDateTime.of(2026, 9, 11, 17, 0),
                policy.retentionCutoff(LocalDateTime.of(2026, 9, 18, 17, 23)));
    }

    @Test
    void retentionCutoffKeepsExactHourWhenNowIsOnTheHour() {
        var policy = new ViewLogRetentionPolicy(7, SHANGHAI);

        assertEquals(LocalDateTime.of(2026, 9, 11, 17, 0),
                policy.retentionCutoff(LocalDateTime.of(2026, 9, 18, 17, 0)));
    }

    @Test
    void detailCutoffKeepsTheExactSevenDayWindow() {
        var policy = new ViewLogRetentionPolicy(7, SHANGHAI);
        var now = LocalDateTime.of(2026, 9, 18, 17, 23);

        assertEquals(LocalDateTime.of(2026, 9, 11, 17, 23), policy.detailCutoff(now));
    }

    @Test
    void bucketStartRoundsAnEventDownToItsHour() {
        var policy = new ViewLogRetentionPolicy(7, SHANGHAI);

        assertEquals(LocalDateTime.of(2026, 9, 18, 17, 0),
                policy.bucketStart(LocalDateTime.of(2026, 9, 18, 17, 59, 59)));
    }

    @Test
    void shanghaiPolicyHasNoDaylightSavingShiftAtTheRetentionBoundary() {
        var policy = new ViewLogRetentionPolicy(7, SHANGHAI);

        assertEquals(LocalDateTime.of(2026, 10, 24, 17, 23),
                policy.detailCutoff(LocalDateTime.of(2026, 10, 31, 17, 23)));
    }

    @Test
    void retentionDaysMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ViewLogRetentionPolicy(0, SHANGHAI));
    }
}
