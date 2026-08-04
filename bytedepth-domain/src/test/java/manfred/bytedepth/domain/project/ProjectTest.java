package manfred.bytedepth.domain.project;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectTest {

    @Test
    void createAndReconstruct_preserveFieldsAndTechnologyList() {
        Project created = Project.create("Site", "desc", "Java, Spring", "git", "demo", 1);
        LocalDateTime time = LocalDateTime.of(2026, 8, 4, 12, 0);
        Project restored = Project.reconstruct(2L, "Site", "desc", "Java, Spring", "git", "demo", 3, time);

        assertNotNull(created.getCreatedAt());
        assertEquals("Site", created.getName());
        assertEquals("desc", created.getDescription());
        assertEquals("Java, Spring", created.getTechStack());
        assertEquals("git", created.getGithubUrl());
        assertEquals("demo", created.getDemoUrl());
        assertEquals(1, created.getSortOrder());
        assertEquals(List.of("Java", "Spring"), created.getTechList());
        assertEquals(2L, restored.getId());
        assertEquals("Site", restored.getName());
        assertEquals("desc", restored.getDescription());
        assertEquals("git", restored.getGithubUrl());
        assertEquals("demo", restored.getDemoUrl());
        assertEquals(3, restored.getSortOrder());
        assertEquals(time, restored.getCreatedAt());
    }

    @Test
    void blankTechnologyList_isEmpty() {
        assertEquals(List.of(), Project.create("Site", "desc", null, "git", "demo", 1).getTechList());
        assertEquals(List.of(), Project.create("Site", "desc", " ", "git", "demo", 1).getTechList());
    }
}
