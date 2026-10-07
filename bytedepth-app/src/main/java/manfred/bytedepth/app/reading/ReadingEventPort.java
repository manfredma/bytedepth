package manfred.bytedepth.app.reading;

import java.time.Instant;
import java.util.List;
import manfred.bytedepth.domain.reading.ReadingEvent;

public interface ReadingEventPort {
  boolean insertIfAbsent(ReadingEvent event);

  List<PendingReadingEvent> findUnprojected(int limit);

  void markProjected(long eventRowId, Instant projectedAt);

  int deleteProjectedBefore(Instant cutoff);
}
