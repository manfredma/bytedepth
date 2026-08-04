package manfred.bytedepth.app.category;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.category.Category;
import manfred.bytedepth.domain.category.CategoryRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListCategoriesQryExe {

    private final CategoryRepository categoryRepository;

    public List<CategoryDTO> execute() {
        List<Category> all = categoryRepository.findAll();
        Map<Long, String> idToName = all.stream()
                .collect(Collectors.toMap(Category::getId, Category::getName));

        // 按树形顺序排列：顶级分类 → 其子分类 → 下一个顶级分类 → ...
        List<Category> topLevel = all.stream()
                .filter(c -> c.getParentId().isEmpty())
                .collect(Collectors.toList());

        Map<Long, List<Category>> childrenMap = all.stream()
                .filter(c -> c.getParentId().isPresent())
                .collect(Collectors.groupingBy(c -> c.getParentId().get()));

        List<CategoryDTO> result = new ArrayList<>();
        for (Category top : topLevel) {
            result.add(toDTO(top, null, 0));
            List<Category> children = childrenMap.getOrDefault(top.getId(), List.of());
            for (Category child : children) {
                String parentName = idToName.get(child.getParentId().orElse(null));
                result.add(toDTO(child, parentName, 1));
            }
        }
        // 兜底：防止有孤儿分类（parentId 指向不存在的顶级分类）
        java.util.Set<Long> topLevelIds = topLevel.stream().map(Category::getId).collect(Collectors.toSet());
        all.stream()
                .filter(c -> c.getParentId().isPresent())
                .filter(c -> !topLevelIds.contains(c.getParentId().get()))
                .forEach(c -> result.add(toDTO(c, "?", 1)));

        return result;
    }

    private CategoryDTO toDTO(Category c, String parentName, int depth) {
        CategoryDTO dto = new CategoryDTO();
        dto.setId(c.getId());
        dto.setName(c.getName());
        dto.setSlug(c.getSlug());
        dto.setParentId(c.getParentId().orElse(null));
        dto.setParentName(parentName);
        dto.setDepth(depth);
        return dto;
    }
}
