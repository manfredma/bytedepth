package manfred.bytedepth.app.category;

import lombok.Data;

@Data
public class CategoryDTO {
    private Long id;
    private String name;
    private String slug;
    private Long parentId;
    private String parentName;
    private int depth;
}
