package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.analytics.PostViewLogPort;
import manfred.bytedepth.app.analytics.ViewLogRetentionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminViewLogControllerRetentionTest {

    @Test
    void detailPagePassesExactSevenDayCutoffToBothQueries() {
        var now = LocalDateTime.of(2026, 9, 18, 17, 23);
        var fixedClock = Clock.fixed(
                now.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        var port = mock(PostViewLogPort.class);
        var retentionPolicy = new ViewLogRetentionPolicy(7, ZoneId.of("Asia/Shanghai"));
        var controller = new AdminViewLogController(port, retentionPolicy, fixedClock);

        controller.list(new ExtendedModelMap(), null, null, 1);

        verify(port).findPage(isNull(), isNull(), eq(now.minusDays(7)), eq(0), eq(20));
        verify(port).countPage(isNull(), isNull(), eq(now.minusDays(7)));
    }
}
