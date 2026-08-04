package manfred.bytedepth.app.project;

import manfred.bytedepth.domain.project.Project;
import manfred.bytedepth.domain.project.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectQueriesAndCommandsTest {

    private final ProjectRepository repository = mock(ProjectRepository.class);

    @Test
    void create_savesAndReturnsNewProjectId() {
        when(repository.save(any())).thenReturn(Project.reconstruct(5L, "Site", "d", "Java", "git", "demo", 1, LocalDateTime.now()));

        assertEquals(5L, new CreateProjectCmdExe(repository).execute("Site", "d", "Java", "git", "demo", 1));
    }

    @Test
    void list_mapsAllPresentationFields() {
        when(repository.findAll()).thenReturn(List.of(Project.reconstruct(5L, "Site", "d", "Java, Spring", "git", "demo", 1, LocalDateTime.now())));

        ProjectDTO dto = new ListProjectsQryExe(repository).execute().getFirst();

        assertEquals(List.of("Java", "Spring"), dto.getTechList());
        assertEquals("git", dto.getGithubUrl());
        assertEquals("demo", dto.getDemoUrl());
    }
}
