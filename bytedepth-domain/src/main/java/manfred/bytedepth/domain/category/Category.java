package manfred.bytedepth.domain.category;

import java.util.Optional;
import lombok.Getter;

@Getter
public class Category {
    private Long id;
    private String name;
    private String slug;
    private Long parentId;

    private Category() {}

    public static Category create(String name, String slug, Long parentId) {
        Category c = new Category();
        c.name = name;
        c.slug = slug;
        c.parentId = parentId;
        return c;
    }

    public static Category reconstruct(Long id, String name, String slug, Long parentId) {
        Category c = new Category();
        c.id = id;
        c.name = name;
        c.slug = slug;
        c.parentId = parentId;
        return c;
    }

    public Optional<Long> getParentId() {
        return Optional.ofNullable(parentId);
    }
}
