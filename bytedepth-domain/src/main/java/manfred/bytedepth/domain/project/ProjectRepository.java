package manfred.bytedepth.domain.project;

import java.util.List;

public interface ProjectRepository {
    Project save(Project project);
    List<Project> findAll();
}
