package manfred.bytedepth.domain.reading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingHistoryProjectorTest {

    private static final Instant FIRST = Instant.parse("2026-10-07T10:00:00Z");
    private static final Instant SECOND = Instant.parse("2026-10-07T10:05:00Z");

    @Test
    void openEventIncrementsReadCountAndSetsFirstAndLastReadTime() {
        ReadingEvent event = event(ReadingEventType.READ_OPEN, 0, FIRST);

        ReadingSummary summary = ReadingHistoryProjector.apply(null, event);

        assertEquals(1, summary.readCount());
        assertEquals(0, summary.totalActiveSeconds());
        assertEquals(FIRST, summary.firstReadAt());
        assertEquals(FIRST, summary.lastReadAt());
    }

    @Test
    void heartbeatAddsOnlyItsDeltaAndMovesLastReadTime() {
        ReadingSummary current = ReadingHistoryProjector.apply(null, event(ReadingEventType.READ_OPEN, 0, FIRST));

        ReadingSummary summary =
                ReadingHistoryProjector.apply(current, event(ReadingEventType.READ_HEARTBEAT, 15, SECOND));

        assertEquals(1, summary.readCount());
        assertEquals(15, summary.totalActiveSeconds());
        assertEquals(FIRST, summary.firstReadAt());
        assertEquals(SECOND, summary.lastReadAt());
    }

    @Test
    void completionAndCloseAlsoAccumulateTheirNonNegativeDeltas() {
        ReadingSummary current = ReadingHistoryProjector.apply(null, event(ReadingEventType.READ_OPEN, 0, FIRST));

        current = ReadingHistoryProjector.apply(current, event(ReadingEventType.READ_COMPLETE, 12, SECOND));
        ReadingSummary summary =
                ReadingHistoryProjector.apply(current, event(ReadingEventType.READ_CLOSE, 3, SECOND.plusSeconds(1)));

        assertEquals(1, summary.readCount());
        assertEquals(15, summary.totalActiveSeconds());
        assertEquals(SECOND.plusSeconds(1), summary.lastReadAt());
    }

    @Test
    void openEventIncrementsAnExistingSummaryAgain() {
        ReadingSummary current = ReadingHistoryProjector.apply(null, event(ReadingEventType.READ_OPEN, 0, FIRST));

        ReadingSummary summary = ReadingHistoryProjector.apply(current, event(ReadingEventType.READ_OPEN, 0, SECOND));

        assertEquals(2, summary.readCount());
        assertEquals(SECOND, summary.lastReadAt());
    }

    @Test
    void rejectsMismatchedSummaryAndInvalidValues() {
        ReadingSummary current = ReadingHistoryProjector.apply(null, event(ReadingEventType.READ_OPEN, 0, FIRST));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReadingHistoryProjector.apply(current, eventFor(9L, 12L, ReadingEventType.READ_OPEN, FIRST)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ReadingHistoryProjector.apply(current, eventFor(7L, 99L, ReadingEventType.READ_OPEN, FIRST)));
        assertThrows(IllegalArgumentException.class, () -> new ReadingSummary(7L, 12L, -1, 0, FIRST, FIRST));
        assertThrows(IllegalArgumentException.class, () -> new ReadingSummary(7L, 12L, 0, -1, FIRST, FIRST));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReadingEvent(
                        UUID.randomUUID(), 0L, 12L, UUID.randomUUID(), ReadingEventType.READ_OPEN, 0, 0, FIRST, FIRST));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReadingEvent(
                        UUID.randomUUID(), 7L, 0L, UUID.randomUUID(), ReadingEventType.READ_OPEN, 0, 0, FIRST, FIRST));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReadingEvent(
                        UUID.randomUUID(),
                        7L,
                        12L,
                        UUID.randomUUID(),
                        ReadingEventType.READ_OPEN,
                        -1,
                        0,
                        FIRST,
                        FIRST));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReadingEvent(
                        UUID.randomUUID(),
                        7L,
                        12L,
                        UUID.randomUUID(),
                        ReadingEventType.READ_OPEN,
                        0,
                        -1,
                        FIRST,
                        FIRST));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ReadingEvent(
                        UUID.randomUUID(),
                        7L,
                        12L,
                        UUID.randomUUID(),
                        ReadingEventType.READ_OPEN,
                        0,
                        101,
                        FIRST,
                        FIRST));
    }

    @Test
    void keepsTheLaterExistingTimestampWhenEventsArriveOutOfOrder() {
        ReadingSummary current = ReadingHistoryProjector.apply(null, event(ReadingEventType.READ_OPEN, 0, SECOND));
        ReadingSummary summary =
                ReadingHistoryProjector.apply(current, event(ReadingEventType.READ_HEARTBEAT, 1, FIRST));
        assertEquals(SECOND, summary.lastReadAt());
    }

    @Test
    void createsSummaryWithoutIncrementingCountWhenFirstEventIsNotOpen() {
        ReadingSummary summary = ReadingHistoryProjector.apply(null, event(ReadingEventType.READ_HEARTBEAT, 2, FIRST));
        assertEquals(0, summary.readCount());
        assertEquals(2, summary.totalActiveSeconds());
    }

    private static ReadingEvent event(ReadingEventType type, int delta, Instant receivedAt) {
        return new ReadingEvent(UUID.randomUUID(), 7L, 12L, UUID.randomUUID(), type, delta, 80, receivedAt, receivedAt);
    }

    private static ReadingEvent eventFor(long userId, long postId, ReadingEventType type, Instant receivedAt) {
        return new ReadingEvent(
                UUID.randomUUID(), userId, postId, UUID.randomUUID(), type, 0, 0, receivedAt, receivedAt);
    }
}
