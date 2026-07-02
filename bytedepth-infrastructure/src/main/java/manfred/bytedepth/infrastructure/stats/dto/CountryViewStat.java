package manfred.bytedepth.infrastructure.stats.dto;

import lombok.Data;

@Data
public class CountryViewStat {
    private String country;
    private long viewCount;
    private double percent;
}
