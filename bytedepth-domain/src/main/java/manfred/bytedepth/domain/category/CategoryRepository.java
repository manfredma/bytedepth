package manfred.bytedepth.domain.category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {
    Category save(Category category);
    Optional<Category> findById(Long id);
    Optional<Category> findBySlug(String slug);
    List<Category> findAll();
    List<Category> findByNameOrSlugLike(String keyword);
    List<Category> findByParentId(Long parentId);
}
