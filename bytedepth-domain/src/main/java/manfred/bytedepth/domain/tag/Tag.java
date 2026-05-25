package manfred.bytedepth.domain.tag;

import lombok.Getter;

@Getter
public class Tag {

    private Long id;
    private String name;
    private String slug;

    private Tag() {}

    public static Tag create(String name, String slug) {
        Tag tag = new Tag();
        tag.name = name;
        tag.slug = slug;
        return tag;
    }

    public static Tag reconstruct(Long id, String name, String slug) {
        Tag tag = new Tag();
        tag.id = id;
        tag.name = name;
        tag.slug = slug;
        return tag;
    }
}
