package manfred.bytedepth.app.project;

import lombok.Data;

import java.util.List;

@Data
public class ProjectDTO {
    private Long id;
    private String name;
    private String description;
    private List<String> techList;
    private String githubUrl;
    private String demoUrl;
}
