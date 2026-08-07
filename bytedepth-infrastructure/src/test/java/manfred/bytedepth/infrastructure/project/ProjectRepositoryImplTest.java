package manfred.bytedepth.infrastructure.project;

import manfred.bytedepth.domain.project.Project;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectRepositoryImplTest {

    private final ProjectMapper projectMapper = mock(ProjectMapper.class);
    private final ProjectRepositoryImpl repository = new ProjectRepositoryImpl(projectMapper);

    @Test
    void save_insertsWhenIdIsNull() {
        Project project = Project.create("MyProject", "desc", "Java,Spring",
            "https://github.com/repo", "https://demo.example.com", 1);
        when(projectMapper.insert(any(ProjectDO.class))).thenAnswer(invocation -> {
            invocation.<ProjectDO>getArgument(0).setId(10L);
            return 1;
        });

        Project saved = repository.save(project);

        assertEquals(10L, saved.getId());
        assertEquals("MyProject", saved.getName());
        assertEquals("desc", saved.getDescription());
        assertEquals("Java,Spring", saved.getTechStack());
        assertEquals("https://github.com/repo", saved.getGithubUrl());
        assertEquals("https://demo.example.com", saved.getDemoUrl());
        assertEquals(1, saved.getSortOrder());
        verify(projectMapper).insert(any(ProjectDO.class));
        verify(projectMapper, never()).updateById(any(ProjectDO.class));
    }

    @Test
    void save_updatesWhenIdIsNotNull() {
        LocalDateTime now = LocalDateTime.now();
        Project project = Project.reconstruct(5L, "Existing", "desc", "Java",
            "https://github.com/repo", "https://demo.example.com", 2, now);

        Project saved = repository.save(project);

        assertEquals(5L, saved.getId());
        assertEquals("Existing", saved.getName());
        assertEquals(2, saved.getSortOrder());
        verify(projectMapper).updateById(any(ProjectDO.class));
        verify(projectMapper, never()).insert(any(ProjectDO.class));
    }

    @Test
    void findAll_mapsAllRowsToEntities() {
        ProjectDO row1 = projectRow();
        row1.setId(1L);
        row1.setName("Project A");
        ProjectDO row2 = projectRow();
        row2.setId(2L);
        row2.setName("Project B");
        when(projectMapper.selectList(any())).thenReturn(List.of(row1, row2));

        List<Project> result = repository.findAll();

        assertEquals(2, result.size());
        assertEquals("Project A", result.get(0).getName());
        assertEquals("Project B", result.get(1).getName());
    }

    @Test
    void findAll_returnsEmptyListWhenNoProjects() {
        when(projectMapper.selectList(any())).thenReturn(List.of());

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void toEntity_defaultsSortOrderToZeroWhenNull() {
        ProjectDO row = projectRow();
        row.setSortOrder(null);

        when(projectMapper.selectList(any())).thenReturn(List.of(row));

        Project result = repository.findAll().get(0);

        assertEquals(0, result.getSortOrder());
    }

    private ProjectDO projectRow() {
        ProjectDO row = new ProjectDO();
        row.setId(1L);
        row.setName("Project A");
        row.setDescription("A description");
        row.setTechStack("Java,Spring");
        row.setGithubUrl("https://github.com/repo");
        row.setDemoUrl("https://demo.example.com");
        row.setSortOrder(1);
        row.setCreatedAt(LocalDateTime.of(2024, 1, 1, 10, 0));
        return row;
    }
}
