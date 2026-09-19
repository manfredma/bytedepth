package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.app.analytics.ArchiveViewLogsCmdExe;
import manfred.bytedepth.app.analytics.ViewLogArchiveRunResult;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViewLogArchiveJobTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 19, 8, 10);
    private static final Clock CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));

    @Test
    void jobRunsUseCaseUnderTheNamedLockWithTheConfiguredLimit() {
        var useCase = mock(ArchiveViewLogsCmdExe.class);
        var adapter = mock(ViewLogArchiveAdapter.class);
        when(adapter.runWithLock(any())).thenAnswer(invocation ->
                ((Supplier<ViewLogArchiveRunResult>) invocation.getArgument(0)).get());
        when(useCase.archive(NOW, 24)).thenReturn(new ViewLogArchiveRunResult(1, 3, 3));

        new ViewLogArchiveJob(useCase, adapter, CLOCK, 24).run();

        verify(useCase).archive(NOW, 24);
    }

    @Test
    void jobDoesNotRunUseCaseWhenNamedLockIsUnavailable() {
        var useCase = mock(ArchiveViewLogsCmdExe.class);
        var adapter = mock(ViewLogArchiveAdapter.class);
        when(adapter.runWithLock(any())).thenReturn(new ViewLogArchiveRunResult(0, 0, 0));

        new ViewLogArchiveJob(useCase, adapter, CLOCK, 24).run();

        verify(useCase, never()).archive(any(), anyInt());
    }

    @Test
    void jobRethrowsArchiveFailureAfterLoggingBoundary() {
        var useCase = mock(ArchiveViewLogsCmdExe.class);
        var adapter = mock(ViewLogArchiveAdapter.class);
        when(adapter.runWithLock(any())).thenThrow(new IllegalStateException("archive failed"));

        assertThrows(IllegalStateException.class, () -> new ViewLogArchiveJob(useCase, adapter, CLOCK, 24).run());
    }

    @Test
    void jobUsesDedicatedSchedulerAndConfigurableFixedDelay() throws NoSuchMethodException {
        var scheduled = ViewLogArchiveJob.class.getMethod("run").getAnnotation(Scheduled.class);

        assertEquals("viewLogArchiveScheduler", scheduled.scheduler());
        assertEquals("${bytedepth.analytics.archive-fixed-delay:10m}", scheduled.fixedDelayString());
    }
}
