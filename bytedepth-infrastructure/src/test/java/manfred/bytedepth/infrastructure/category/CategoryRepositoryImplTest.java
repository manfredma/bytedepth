package manfred.bytedepth.infrastructure.category;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryRepositoryImplTest {

    private final CategoryMapper categoryMapper = Mockito.mock(CategoryMapper.class);
    private final CategoryRepositoryImpl repository = new CategoryRepositoryImpl(categoryMapper);

    // ---- save ----

    @Test
    void save_insertsNewCategoryWhenIdIsNull() {
        when(categoryMapper.insert(any(CategoryDO.class))).thenAnswer(invocation -> {
            invocation.<CategoryDO>getArgument(0).setId(1L);
            return 1;
        });

        var category = manfred.bytedepth.domain.category.Category.create("Java", "java", 2L);
        var saved = repository.save(category);

        assertEquals(1L, saved.getId());
        assertEquals("Java", saved.getName());
        assertEquals("java", saved.getSlug());
        assertEquals(Optional.of(2L), saved.getParentId());
        verify(categoryMapper).insert(any(CategoryDO.class));
    }

    @Test
    void save_updatesExistingCategoryWhenIdNotNull() {
        var category = manfred.bytedepth.domain.category.Category.reconstruct(5L, "Spring", "spring", 2L);

        var saved = repository.save(category);

        assertEquals(5L, saved.getId());
        assertEquals("Spring", saved.getName());
        verify(categoryMapper).updateById(any(CategoryDO.class));
    }

    @Test
    void save_handlesNullParentId() {
        when(categoryMapper.insert(any(CategoryDO.class))).thenAnswer(invocation -> {
            invocation.<CategoryDO>getArgument(0).setId(1L);
            return 1;
        });

        var category = manfred.bytedepth.domain.category.Category.create("Root", "root", null);
        var saved = repository.save(category);

        assertTrue(saved.getParentId().isEmpty());
    }

    // ---- findById ----

    @Test
    void findById_returnsEntityWhenFound() {
        when(categoryMapper.selectById(1L)).thenReturn(categoryRow(1L));

        var result = repository.findById(1L);

        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getId());
        assertEquals("Java", result.get().getName());
    }

    @Test
    void findById_returnsEmptyWhenNotFound() {
        when(categoryMapper.selectById(99L)).thenReturn(null);

        assertTrue(repository.findById(99L).isEmpty());
    }

    // ---- findBySlug ----

    @Test
    void findBySlug_returnsEntityWhenFound() {
        when(categoryMapper.selectOne(any())).thenReturn(categoryRow(1L));

        var result = repository.findBySlug("java");

        assertTrue(result.isPresent());
        assertEquals("java", result.get().getSlug());
    }

    @Test
    void findBySlug_returnsEmptyWhenNotFound() {
        when(categoryMapper.selectOne(any())).thenReturn(null);

        assertTrue(repository.findBySlug("missing").isEmpty());
    }

    // ---- findAll ----

    @Test
    void findAll_mapsResults() {
        when(categoryMapper.selectList(isNull())).thenReturn(List.of(categoryRow(1L), categoryRow(2L)));

        var categories = repository.findAll();

        assertEquals(2, categories.size());
        assertEquals(1L, categories.get(0).getId());
        assertEquals(2L, categories.get(1).getId());
    }

    @Test
    void findAll_emptyResultReturnsEmpty() {
        when(categoryMapper.selectList(isNull())).thenReturn(List.of());

        assertTrue(repository.findAll().isEmpty());
    }

    // ---- findByParentId ----

    @Test
    void findByParentId_mapsResultsWhenParentIdNotNull() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(categoryRow(1L)));

        var categories = repository.findByParentId(2L);

        assertEquals(1, categories.size());
        assertEquals(1L, categories.get(0).getId());
    }

    @Test
    void findByParentId_mapsResultsWhenParentIdNull() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(categoryRow(1L)));

        var categories = repository.findByParentId(null);

        assertEquals(1, categories.size());
        assertEquals(1L, categories.get(0).getId());
    }

    @Test
    void findByParentId_emptyResultReturnsEmpty() {
        when(categoryMapper.selectList(any())).thenReturn(List.of());

        assertTrue(repository.findByParentId(2L).isEmpty());
    }

    // ---- helpers ----

    private CategoryDO categoryRow(Long id) {
        CategoryDO row = new CategoryDO();
        row.setId(id);
        row.setName("Java");
        row.setSlug("java");
        row.setParentId(2L);
        return row;
    }
}
