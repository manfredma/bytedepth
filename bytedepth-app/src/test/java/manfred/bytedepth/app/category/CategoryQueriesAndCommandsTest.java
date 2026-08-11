package manfred.bytedepth.app.category;

import manfred.bytedepth.domain.category.Category;
import manfred.bytedepth.domain.category.CategoryRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoryQueriesAndCommandsTest {

    private final CategoryRepository repository = mock(CategoryRepository.class);

    @Test
    void create_persistsCategoryAndReturnsItsId() {
        when(repository.save(any())).thenReturn(Category.reconstruct(8L, "Java", "java", null));

        Long id = new CreateCategoryCmdExe(repository).execute("Java", "java", null);

        assertEquals(8L, id);
        verify(repository).save(argThat(c -> "Java".equals(c.getName()) && c.getParentId().isEmpty()));
    }

    @Test
    void list_ordersTreesAndIncludesOrphansWithFallbackParent() {
        when(repository.findAll()).thenReturn(List.of(
                Category.reconstruct(1L, "Java", "java", null),
                Category.reconstruct(2L, "Spring", "spring", 1L),
                Category.reconstruct(3L, "Orphan", "orphan", 99L)));

        List<CategoryDTO> categories = new ListCategoriesQryExe(repository).execute();

        assertEquals(List.of("Java", "Spring", "Orphan"), categories.stream().map(CategoryDTO::getName).toList());
        assertEquals(0, categories.get(0).getDepth());
        assertEquals("Java", categories.get(1).getParentName());
        assertEquals("?", categories.get(2).getParentName());
    }

    @Test
    void filteredList_includesMatchingChildAndItsParentCaseInsensitively() {
        when(repository.findAll()).thenReturn(List.of(
                Category.reconstruct(1L, "Java", "java", null),
                Category.reconstruct(2L, "Spring", "spring", 1L)));

        List<CategoryDTO> categories = new ListCategoriesQryExe(repository).executeFiltered("SPR", null);

        assertEquals(List.of("Java", "Spring"), categories.stream().map(CategoryDTO::getName).toList());
        assertEquals("Java", categories.get(1).getParentName());
    }

    @Test
    void filteredList_matchesSlug() {
        when(repository.findAll()).thenReturn(List.of(Category.reconstruct(1L, "Java", "backend-java", null)));

        List<CategoryDTO> categories = new ListCategoriesQryExe(repository).executeFiltered(null, "END-J");

        assertEquals(List.of("Java"), categories.stream().map(CategoryDTO::getName).toList());
    }

    @Test
    void filteredList_keepsAlreadyMatchedParentWithoutAddingItAgain() {
        when(repository.findAll()).thenReturn(List.of(
                Category.reconstruct(1L, "Java", "java", null),
                Category.reconstruct(2L, "Java Web", "java-web", 1L)));

        List<CategoryDTO> categories = new ListCategoriesQryExe(repository).executeFiltered("java", null);

        assertEquals(List.of("Java", "Java Web"), categories.stream().map(CategoryDTO::getName).toList());
        assertEquals("Java", categories.get(1).getParentName());
    }

    @Test
    void filteredList_acceptsNameWhenTheOptionalSlugDoesNotMatch() {
        when(repository.findAll()).thenReturn(List.of(
                Category.reconstruct(1L, "Java", "java", null),
                Category.reconstruct(2L, "Kotlin", "kotlin", null)));

        List<CategoryDTO> categories = new ListCategoriesQryExe(repository).executeFiltered("java", "missing");

        assertEquals(List.of("Java"), categories.stream().map(CategoryDTO::getName).toList());
    }

    @Test
    void filteredList_withoutFiltersUsesNormalTreeQuery() {
        when(repository.findAll()).thenReturn(List.of(Category.reconstruct(1L, "Java", "java", null)));

        assertEquals(1, new ListCategoriesQryExe(repository).executeFiltered(" ", "").size());
        verify(repository, times(1)).findAll();
    }
}
