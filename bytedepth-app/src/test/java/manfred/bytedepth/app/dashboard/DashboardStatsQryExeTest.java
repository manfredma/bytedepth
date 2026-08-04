package manfred.bytedepth.app.dashboard;

import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.project.ProjectRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DashboardStatsQryExeTest {
    @Test
    void execute_reportsAllDashboardCounters() {
        PostRepository posts = mock(PostRepository.class);
        ProjectRepository projects = mock(ProjectRepository.class);
        when(posts.countAll()).thenReturn(9L);
        when(posts.countPublished()).thenReturn(7L);
        when(projects.findAll()).thenReturn(List.of(mock(manfred.bytedepth.domain.project.Project.class)));

        DashboardStatsDTO stats = new DashboardStatsQryExe(posts, projects).execute();

        assertEquals(9, stats.getTotalPosts());
        assertEquals(7, stats.getPublishedPosts());
        assertEquals(0, stats.getPendingComments());
        assertEquals(1, stats.getTotalProjects());
    }
}
