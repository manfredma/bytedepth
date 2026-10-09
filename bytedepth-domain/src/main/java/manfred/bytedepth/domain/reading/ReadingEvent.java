package manfred.bytedepth.domain.reading;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ReadingEvent(
        UUID eventId,
        Long userId,
        Long postId,
        UUID sessionId,
        ReadingEventType type,
        int activeSecondsDelta,
        int maxScrollDepth,
        Instant occurredAt,
        Instant receivedAt) {

    public ReadingEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(postId, "postId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(receivedAt, "receivedAt");
        if (userId <= 0 || postId <= 0 || activeSecondsDelta < 0 || maxScrollDepth < 0 || maxScrollDepth > 100) {
            throw new IllegalArgumentException("reading event values are out of range");
        }
    }
}
