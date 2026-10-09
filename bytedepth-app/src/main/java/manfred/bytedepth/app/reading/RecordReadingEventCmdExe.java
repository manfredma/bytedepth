package manfred.bytedepth.app.reading;

import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.domain.reading.ReadingEvent;
import manfred.bytedepth.domain.reading.ReadingEventType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecordReadingEventCmdExe {

    private static final int MAX_ACTIVE_SECONDS_DELTA = 60;
    private final GetPostQryExe getPostQryExe;
    private final ReadingEventPort eventPort;

    public boolean execute(long userId, String slug, ReadingEventRequest request) {
        var post = getPostQryExe.executeBySlug(slug);
        if (!"PUBLISHED".equals(post.getStatus()) || !request.valid()) {
            throw new IllegalArgumentException("invalid reading event");
        }
        if (request.activeSecondsDelta() > MAX_ACTIVE_SECONDS_DELTA) {
            throw new IllegalArgumentException("active reading delta is too large");
        }
        return eventPort.insertIfAbsent(new ReadingEvent(
                request.eventId(),
                userId,
                post.getId(),
                request.sessionId(),
                request.type(),
                request.activeSecondsDelta(),
                request.maxScrollDepth(),
                request.occurredAt(),
                Instant.now()));
    }

    public record ReadingEventRequest(
            UUID eventId,
            UUID sessionId,
            ReadingEventType type,
            int activeSecondsDelta,
            int maxScrollDepth,
            Instant occurredAt) {
        boolean valid() {
            return eventId != null
                    && sessionId != null
                    && type != null
                    && activeSecondsDelta >= 0
                    && maxScrollDepth >= 0
                    && maxScrollDepth <= 100;
        }
    }
}
