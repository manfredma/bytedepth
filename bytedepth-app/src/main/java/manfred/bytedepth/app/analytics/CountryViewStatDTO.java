package manfred.bytedepth.app.analytics;

import lombok.Data;

@Data
public class CountryViewStatDTO {
    private String country;
    private long viewCount;
    private double percent;
}
