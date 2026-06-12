package manfred.bytedepth.domain.series;

import java.time.LocalDateTime;

public record SeriesPostItem(
        Long id,
        String slug,
        String title,
        Integer seriesOrder,
        String content,
        String status,
        LocalDateTime publishedAt
) {
    /** 兼容旧调用方（slug/content/status/publishedAt 为 null）*/
    public SeriesPostItem(Long id, String title, Integer seriesOrder) {
        this(id, null, title, seriesOrder, null, null, null);
    }
}
