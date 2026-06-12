package manfred.bytedepth.domain.search;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PostSearchDoc {
    private Long id;
    private String slug;
    private String title;
    private String content;
    private String categoryName;
    private String categorySlug;
    private List<String> tags;
    private String seriesName;
}
