package manfred.bytedepth.domain.project;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Getter
public class Project {

    private Long id;
    private String name;
    private String description;
    private String techStack;
    private String githubUrl;
    private String demoUrl;
    private Integer sortOrder;
    private LocalDateTime createdAt;

    private Project() {}

    public static Project create(String name, String description, String techStack,
                                  String githubUrl, String demoUrl, int sortOrder) {
        Project p = new Project();
        p.name = name;
        p.description = description;
        p.techStack = techStack;
        p.githubUrl = githubUrl;
        p.demoUrl = demoUrl;
        p.sortOrder = sortOrder;
        p.createdAt = LocalDateTime.now();
        return p;
    }

    public static Project reconstruct(Long id, String name, String description, String techStack,
                                       String githubUrl, String demoUrl, int sortOrder,
                                       LocalDateTime createdAt) {
        Project p = new Project();
        p.id = id;
        p.name = name;
        p.description = description;
        p.techStack = techStack;
        p.githubUrl = githubUrl;
        p.demoUrl = demoUrl;
        p.sortOrder = sortOrder;
        p.createdAt = createdAt;
        return p;
    }

    public List<String> getTechList() {
        if (techStack == null || techStack.isBlank()) return List.of();
        return Arrays.stream(techStack.split(",")).map(String::trim).toList();
    }
}
