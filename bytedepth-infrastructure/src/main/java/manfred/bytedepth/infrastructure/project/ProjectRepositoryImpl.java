package manfred.bytedepth.infrastructure.project;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.project.Project;
import manfred.bytedepth.domain.project.ProjectRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class ProjectRepositoryImpl implements ProjectRepository {

    private final ProjectMapper projectMapper;

    @Override
    public Project save(Project project) {
        ProjectDO d = toDO(project);
        if (project.getId() == null) {
            projectMapper.insert(d);
        } else {
            projectMapper.updateById(d);
        }
        return toEntity(d);
    }

    @Override
    public List<Project> findAll() {
        return projectMapper.selectList(new LambdaQueryWrapper<ProjectDO>()
                .orderByAsc(ProjectDO::getSortOrder))
                .stream().map(this::toEntity).collect(Collectors.toList());
    }

    private ProjectDO toDO(Project p) {
        ProjectDO d = new ProjectDO();
        d.setId(p.getId());
        d.setName(p.getName());
        d.setDescription(p.getDescription());
        d.setTechStack(p.getTechStack());
        d.setGithubUrl(p.getGithubUrl());
        d.setDemoUrl(p.getDemoUrl());
        d.setSortOrder(p.getSortOrder());
        d.setCreatedAt(p.getCreatedAt());
        return d;
    }

    private Project toEntity(ProjectDO d) {
        return Project.reconstruct(d.getId(), d.getName(), d.getDescription(),
                d.getTechStack(), d.getGithubUrl(), d.getDemoUrl(),
                d.getSortOrder() == null ? 0 : d.getSortOrder(), d.getCreatedAt());
    }
}
