package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.SecurityUtils;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.reading.RecordReadingEventCmdExe;
import manfred.bytedepth.domain.reading.ReadingEventType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class ReadingEventController {

    private final RecordReadingEventCmdExe recordReadingEventCmdExe;
    private final VisitRequestFilter visitRequestFilter;

    @PostMapping("/{slug}/reading-events")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> record(
            @PathVariable String slug, @RequestBody RecordReadingEventRequest request, HttpServletRequest httpRequest) {
        Long userId = SecurityUtils.extractUserId(SecurityUtils.currentUser());
        if (userId == null) {
            return ResponseEntity.status(401).build();
        }
        if (!visitRequestFilter.shouldRecord(VisitRequestFilter.Request.from(httpRequest))) {
            return ResponseEntity.accepted().build();
        }
        try {
            recordReadingEventCmdExe.execute(
                    userId,
                    slug,
                    new RecordReadingEventCmdExe.ReadingEventRequest(
                            request.eventId(),
                            request.sessionId(),
                            request.type(),
                            request.activeSecondsDelta(),
                            request.maxScrollDepth(),
                            request.occurredAt()));
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    public record RecordReadingEventRequest(
            UUID eventId,
            UUID sessionId,
            ReadingEventType type,
            int activeSecondsDelta,
            int maxScrollDepth,
            Instant occurredAt) {}
}
