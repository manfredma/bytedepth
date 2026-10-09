package manfred.bytedepth.infrastructure.reading;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import manfred.bytedepth.app.reading.PendingReadingEvent;
import manfred.bytedepth.app.reading.ReadingEventPort;
import manfred.bytedepth.app.reading.ReadingHistoryPort;
import manfred.bytedepth.domain.reading.ReadingEvent;
import manfred.bytedepth.domain.reading.ReadingEventType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class ReadingProjectionJobTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

    private final ReadingEventPort eventPort = mock(ReadingEventPort.class);
    private final ReadingHistoryPort historyPort = mock(ReadingHistoryPort.class);
    private final TransactionTemplate transactionTemplate = synchronousTransactionTemplate();
    private final ReadingProjectionJob job =
            new ReadingProjectionJob(eventPort, historyPort, transactionTemplate, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void projectsPendingEventsAndDeletesExpiredEvents() {
        var event = event();
        when(eventPort.findUnprojected(100)).thenReturn(List.of(new PendingReadingEvent(41L, event)));
        when(historyPort.findByUserAndPost(7L, 12L)).thenReturn(null);
        when(eventPort.deleteProjectedBefore(any())).thenReturn(0);

        job.run();

        verify(historyPort).upsert(event);
        verify(eventPort).markProjected(eq(41L), eq(NOW));
        verify(eventPort).deleteProjectedBefore(eq(NOW.minusSeconds(7 * 24 * 60 * 60)));
    }

    @Test
    void stillDeletesExpiredEventsWhenThereIsNothingToProject() {
        when(eventPort.findUnprojected(100)).thenReturn(List.of());
        when(eventPort.deleteProjectedBefore(any())).thenReturn(1);

        job.run();

        verify(eventPort).deleteProjectedBefore(eq(NOW.minusSeconds(7 * 24 * 60 * 60)));
    }

    @Test
    void doesNotLogWhenThereIsNothingToProjectOrDelete() {
        when(eventPort.findUnprojected(100)).thenReturn(List.of());
        when(eventPort.deleteProjectedBefore(any())).thenReturn(0);

        job.run();

        verify(eventPort).deleteProjectedBefore(eq(NOW.minusSeconds(7 * 24 * 60 * 60)));
    }

    private static ReadingEvent event() {
        return new ReadingEvent(
                java.util.UUID.randomUUID(),
                7L,
                12L,
                java.util.UUID.randomUUID(),
                ReadingEventType.READ_OPEN,
                0,
                80,
                NOW,
                NOW);
    }

    @SuppressWarnings("unchecked")
    private static TransactionTemplate synchronousTransactionTemplate() {
        var template = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
                    ((java.util.function.Consumer<TransactionStatus>) invocation.getArgument(0))
                            .accept(mock(TransactionStatus.class));
                    return null;
                })
                .when(template)
                .executeWithoutResult(any());
        return template;
    }
}
