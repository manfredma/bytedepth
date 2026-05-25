package manfred.bytedepth.app.project;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.project.ProjectRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ListProjectsQryExe {

    private final ProjectRepository projectRepository;

    public List<ProjectDTO> execute() {
        return projectRepository.findAll().stream().map(p -> {
            ProjectDTO dto = new ProjectDTO();
            dto.setId(p.getId());
            dto.setName(p.getName());
            dto.setDescription(p.getDescription());
            dto.setTechList(p.getTechList());
            dto.setGithubUrl(p.getGithubUrl());
            dto.setDemoUrl(p.getDemoUrl());
            return dto;
        }).collect(Collectors.toList());
    }
}
