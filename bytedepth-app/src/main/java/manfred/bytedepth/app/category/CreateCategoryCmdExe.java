package manfred.bytedepth.app.category;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.category.Category;
import manfred.bytedepth.domain.category.CategoryRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateCategoryCmdExe {

    private final CategoryRepository categoryRepository;

    public Long execute(String name, String slug, Long parentId) {
        Category category = Category.create(name, slug, parentId);
        Category saved = categoryRepository.save(category);
        return saved.getId();
    }
}
