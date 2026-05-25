package manfred.bytedepth.infrastructure.category;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.category.Category;
import manfred.bytedepth.domain.category.CategoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CategoryRepositoryImpl implements CategoryRepository {

    private final CategoryMapper categoryMapper;

    @Override
    public Category save(Category category) {
        CategoryDO categoryDO = toDO(category);
        if (category.getId() == null) {
            categoryMapper.insert(categoryDO);
        } else {
            categoryMapper.updateById(categoryDO);
        }
        return toEntity(categoryDO);
    }

    @Override
    public Optional<Category> findById(Long id) {
        return Optional.ofNullable(categoryMapper.selectById(id)).map(this::toEntity);
    }

    @Override
    public Optional<Category> findBySlug(String slug) {
        return Optional.ofNullable(categoryMapper.selectOne(
                new LambdaQueryWrapper<CategoryDO>().eq(CategoryDO::getSlug, slug)))
                .map(this::toEntity);
    }

    @Override
    public List<Category> findAll() {
        return categoryMapper.selectList(null).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<Category> findByParentId(Long parentId) {
        LambdaQueryWrapper<CategoryDO> wrapper = parentId == null
                ? new LambdaQueryWrapper<CategoryDO>().isNull(CategoryDO::getParentId)
                : new LambdaQueryWrapper<CategoryDO>().eq(CategoryDO::getParentId, parentId);
        return categoryMapper.selectList(wrapper).stream().map(this::toEntity).collect(Collectors.toList());
    }

    private CategoryDO toDO(Category c) {
        CategoryDO d = new CategoryDO();
        d.setId(c.getId());
        d.setName(c.getName());
        d.setSlug(c.getSlug());
        d.setParentId(c.getParentId().orElse(null));
        return d;
    }

    private Category toEntity(CategoryDO d) {
        return Category.reconstruct(d.getId(), d.getName(), d.getSlug(), d.getParentId());
    }
}
