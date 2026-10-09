package manfred.bytedepth.domain.search;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

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
