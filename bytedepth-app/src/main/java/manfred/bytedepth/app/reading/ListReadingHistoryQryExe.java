package manfred.bytedepth.app.reading;

import java.util.List;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.reading.ReadingHistoryEntry;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ListReadingHistoryQryExe {
    private static final int PAGE_SIZE = 20;
    private final ReadingHistoryPort historyPort;

    public List<ReadingHistoryEntry> execute(long userId, String cursor) {
        return historyPort.findPageByUser(userId, cursor, PAGE_SIZE);
    }
}
