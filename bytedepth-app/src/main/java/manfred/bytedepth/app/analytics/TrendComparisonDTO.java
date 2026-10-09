package manfred.bytedepth.app.analytics;

import java.util.List;
import lombok.Data;

/** 当前统计区间及其紧邻的前一等长区间的趋势数据。 */
@Data
public class TrendComparisonDTO {
    private List<TrendPointDTO> current;
    private List<TrendPointDTO> previous;
    private String currentPeriod;
    private String previousPeriod;
}
