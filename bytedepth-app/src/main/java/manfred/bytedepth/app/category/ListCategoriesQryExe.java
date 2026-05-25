package manfred.bytedepth.app.category;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.category.Category;
import manfred.bytedepth.domain.category.CategoryRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListCategoriesQryExe {

    private final CategoryRepository categoryRepository;

    public List<CategoryDTO> execute() {
        return categoryRepository.findAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    private CategoryDTO toDTO(Category c) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setSlug(c.getSlug());
        dto.setParentId(c.getParentId().orElse(null));
        return dto;
    }
}
