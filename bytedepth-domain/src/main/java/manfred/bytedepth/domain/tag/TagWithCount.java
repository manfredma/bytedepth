package manfred.bytedepth.domain.tag;

import lombok.Getter;

@Getter
public class TagWithCount {
    private final Long id;
    private final String name;
    private final String slug;
    private final long count;

    public TagWithCount(Long id, String name, String slug, long count) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.count = count;
    }
}
