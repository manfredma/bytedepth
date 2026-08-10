package manfred.bytedepth.app.analytics;

import lombok.Data;

/** 页面访问排名 DTO，percent 由 Controller 回填。 */
@Data
public class PageViewRankDTO {
    private String pagePath;
    private long viewCount;
    private double percent;
}