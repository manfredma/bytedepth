package manfred.bytedepth.infrastructure.stats;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import manfred.bytedepth.app.analytics.ViewLogArchiveSource;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ViewLogArchiveBucket {

  private ViewLogArchiveSource source;
  private LocalDateTime bucketStart;
  private LocalDateTime bucketEnd;
  private long archivedRowCount;
}
