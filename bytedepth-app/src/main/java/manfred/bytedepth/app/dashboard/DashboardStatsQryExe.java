package manfred.bytedepth.app.dashboard;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.comment.CommentRepository;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.project.ProjectRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DashboardStatsQryExe {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ProjectRepository projectRepository;

    public DashboardStatsDTO execute() {
        DashboardStatsDTO dto = new DashboardStatsDTO();
        dto.setTotalPosts(postRepository.countAll());
        dto.setPublishedPosts(postRepository.countPublished());
        dto.setPendingComments(commentRepository.findPending().size());
        dto.setTotalProjects(projectRepository.findAll().size());
        return dto;
    }
}
