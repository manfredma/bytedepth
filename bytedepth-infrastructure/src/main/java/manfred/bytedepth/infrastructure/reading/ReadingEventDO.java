package manfred.bytedepth.infrastructure.reading;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Data;

@Data
public class ReadingEventDO {
  private Long id;
  private UUID eventId;
  private Long userId;
  private Long postId;
  private UUID sessionId;
  private String eventType;
  private Integer activeSecondsDelta;
  private Integer maxScrollDepth;
  private LocalDateTime occurredAt;
  private LocalDateTime receivedAt;
  private LocalDateTime projectedAt;
}
