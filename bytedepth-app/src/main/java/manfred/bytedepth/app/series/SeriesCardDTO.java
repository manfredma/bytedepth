package manfred.bytedepth.app.series;

import lombok.Data;

@Data
public class SeriesCardDTO {
    private Long id;
    private String name;
    private String slug;
    private String description;
    private long postCount;        // 已发布文章数
    private String firstSummary;   // 第一篇已发布文章 content 前 160 字，可为 null
}
