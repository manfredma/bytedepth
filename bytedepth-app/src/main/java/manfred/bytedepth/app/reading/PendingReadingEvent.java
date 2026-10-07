package manfred.bytedepth.app.reading;

import manfred.bytedepth.domain.reading.ReadingEvent;

public record PendingReadingEvent(long rowId, ReadingEvent event) {}
