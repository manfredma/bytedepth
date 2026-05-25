package manfred.bytedepth.domain.category;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CategoryTest {

    @Test
    void create_shouldSetFields() {
        Category c = Category.create("技术", "tech", null);
        assertEquals("技术", c.getName());
        assertEquals("tech", c.getSlug());
        assertTrue(c.getParentId().isEmpty());
        assertNull(c.getId());
    }

    @Test
    void create_withParent_shouldSetParentId() {
        Category c = Category.create("Java", "java", 1L);
        assertTrue(c.getParentId().isPresent());
        assertEquals(1L, c.getParentId().get());
    }

    @Test
    void reconstruct_shouldRestoreAllFields() {
        Category c = Category.reconstruct(5L, "前端", "frontend", 2L);
        assertEquals(5L, c.getId());
        assertEquals("前端", c.getName());
        assertEquals("frontend", c.getSlug());
        assertEquals(2L, c.getParentId().get());
    }

    @Test
    void reconstruct_withNullParent_shouldReturnEmptyOptional() {
        Category c = Category.reconstruct(3L, "后端", "backend", null);
        assertEquals(3L, c.getId());
        assertTrue(c.getParentId().isEmpty());
    }
}
