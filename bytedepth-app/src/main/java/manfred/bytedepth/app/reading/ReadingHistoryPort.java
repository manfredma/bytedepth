package manfred.bytedepth.app.reading;

import java.util.List;
import manfred.bytedepth.domain.reading.ReadingEvent;
import manfred.bytedepth.domain.reading.ReadingHistoryEntry;
import manfred.bytedepth.domain.reading.ReadingSummary;

public interface ReadingHistoryPort {
  void upsert(ReadingEvent event);

  ReadingSummary findByUserAndPost(long userId, long postId);

  List<ReadingHistoryEntry> findPageByUser(long userId, String cursor, int limit);
}
