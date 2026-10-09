package manfred.bytedepth.app.reading;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.reading.ReadingSummary;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GetReadingSummaryQryExe {
    private final ReadingHistoryPort historyPort;

    public ReadingSummary execute(long userId, long postId) {
        return historyPort.findByUserAndPost(userId, postId);
    }
}
