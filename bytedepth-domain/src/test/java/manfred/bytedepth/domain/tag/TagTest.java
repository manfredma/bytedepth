package manfred.bytedepth.domain.tag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TagTest {

    @Test
    void create_shouldSetNameAndSlug() {
        Tag tag = Tag.create("Java", "java");
        assertEquals("Java", tag.getName());
        assertEquals("java", tag.getSlug());
        assertNull(tag.getId());
    }

    @Test
    void create_shouldNotSetId() {
        Tag tag = Tag.create("Spring Boot", "spring-boot");
        assertNull(tag.getId());
    }

    @Test
    void reconstruct_shouldRestoreAllFields() {
        Tag tag = Tag.reconstruct(7L, "MyBatis", "mybatis");
        assertEquals(7L, tag.getId());
        assertEquals("MyBatis", tag.getName());
        assertEquals("mybatis", tag.getSlug());
    }

    @Test
    void reconstruct_shouldAllowNullId() {
        Tag tag = Tag.reconstruct(null, "Docker", "docker");
        assertNull(tag.getId());
        assertEquals("Docker", tag.getName());
        assertEquals("docker", tag.getSlug());
    }
}
