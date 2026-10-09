package manfred.bytedepth.infrastructure.reading;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.app.reading.ReadingEventPort;
import manfred.bytedepth.app.reading.ReadingHistoryPort;
import manfred.bytedepth.domain.reading.ReadingHistoryProjector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadingProjectionJob {

    private final ReadingEventPort eventPort;
    private final ReadingHistoryPort historyPort;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${bytedepth.reading-history.projection-delay:2s}")
    public void run() {
        var events = eventPort.findUnprojected(100);
        for (var pending : events) {
            transactionTemplate.executeWithoutResult(status -> {
                var current = historyPort.findByUserAndPost(
                        pending.event().userId(), pending.event().postId());
                historyPort.upsert(pending.event());
                ReadingHistoryProjector.apply(current, pending.event());
                eventPort.markProjected(pending.rowId(), Instant.now(clock));
            });
        }
        int deleted = eventPort.deleteProjectedBefore(Instant.now(clock).minus(Duration.ofDays(7)));
        if (!events.isEmpty() || deleted > 0) {
            log.info("Reading history projection completed: events={}, deleted={}", events.size(), deleted);
        }
    }
}
