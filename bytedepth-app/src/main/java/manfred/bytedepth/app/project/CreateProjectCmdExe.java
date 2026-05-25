package manfred.bytedepth.app.project;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.project.Project;
import manfred.bytedepth.domain.project.ProjectRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CreateProjectCmdExe {

    private final ProjectRepository projectRepository;

    public Long execute(String name, String description, String techStack,
                        String githubUrl, String demoUrl, int sortOrder) {
        Project project = Project.create(name, description, techStack, githubUrl, demoUrl, sortOrder);
        return projectRepository.save(project).getId();
    }
}
