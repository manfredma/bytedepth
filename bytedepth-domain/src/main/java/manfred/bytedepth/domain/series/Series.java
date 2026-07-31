package manfred.bytedepth.domain.series;

import lombok.Getter;

@Getter
public class Series {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private Long authorId;

    private Series() {}

    public static Series create(String name, String slug, String description) {
        return create(name, slug, description, null);
    }

    public static Series create(String name, String slug, String description, Long authorId) {
        Series s = new Series();
        s.name = name;
        s.slug = slug;
        s.description = description;
        s.authorId = authorId;
        return s;
    }

    public static Series reconstruct(Long id, String name, String slug, String description) {
        return reconstruct(id, name, slug, description, null);
    }

    public static Series reconstruct(Long id, String name, String slug, String description, Long authorId) {
        Series s = new Series();
        s.id = id;
        s.name = name;
        s.slug = slug;
        s.description = description;
        s.authorId = authorId;
        return s;
    }
}
