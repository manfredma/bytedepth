package manfred.bytedepth.app.dashboard;

import lombok.Data;

@Data
public class DashboardStatsDTO {
    private long totalPosts;
    private long publishedPosts;
    private long pendingComments;
    private long totalProjects;
}
