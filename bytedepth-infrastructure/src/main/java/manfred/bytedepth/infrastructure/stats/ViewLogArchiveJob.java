package manfred.bytedepth.infrastructure.stats;

import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.app.analytics.ArchiveViewLogsCmdExe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Slf4j
@Component
public class ViewLogArchiveJob {

    private final ArchiveViewLogsCmdExe archiveViewLogsCmdExe;
    private final ViewLogArchiveAdapter archiveAdapter;
    private final Clock clock;
    private final int maxBuckets;

    public ViewLogArchiveJob(ArchiveViewLogsCmdExe archiveViewLogsCmdExe,
                             ViewLogArchiveAdapter archiveAdapter,
                             Clock clock,
                             @Value("${bytedepth.analytics.archive-max-buckets-per-run:24}") int maxBuckets) {
        this.archiveViewLogsCmdExe = archiveViewLogsCmdExe;
        this.archiveAdapter = archiveAdapter;
        this.clock = clock;
        this.maxBuckets = maxBuckets;
    }

    @Scheduled(fixedDelayString = "${bytedepth.analytics.archive-fixed-delay:10m}",
            scheduler = "viewLogArchiveScheduler")
    public void run() {
        try {
            var result = archiveAdapter.runWithLock(() ->
                    archiveViewLogsCmdExe.archive(LocalDateTime.now(clock), maxBuckets));
            log.info("View log archive run completed: buckets={}, aggregatedRows={}, deletedRows={}",
                    result.bucketCount(), result.aggregatedRows(), result.deletedRows());
        } catch (RuntimeException exception) {
            log.error("View log archive run failed", exception);
            throw exception;
        }
    }
}
