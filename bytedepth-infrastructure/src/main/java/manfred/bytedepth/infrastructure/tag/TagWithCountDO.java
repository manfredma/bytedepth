package manfred.bytedepth.infrastructure.tag;

import lombok.Data;

@Data
public class TagWithCountDO {
    private Long id;
    private String name;
    private String slug;
    private long postCount;
}
