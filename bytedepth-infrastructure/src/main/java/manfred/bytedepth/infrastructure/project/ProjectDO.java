package manfred.bytedepth.infrastructure.project;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("project")
public class ProjectDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String techStack;
    private String githubUrl;
    private String demoUrl;
    private Integer sortOrder;
    private LocalDateTime createdAt;
}
