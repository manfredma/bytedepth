package manfred.bytedepth.app.analytics;

import lombok.Data;

import java.util.List;

/** 总体访问趋势，仅包含当前统计区间，不提供前一周期对比。 */
@Data
public class OverviewTrendDTO {
    private List<TrendPointDTO> current;
    private String currentPeriod;
}
