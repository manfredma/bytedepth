package manfred.bytedepth.app.post.command;

import com.alibaba.cola.dto.Command;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreatePostCmd extends Command {
    private String title;
    private String content;
    private Long categoryId;
    /** 发文章的用户名（由 Controller 从 SecurityContext 取得），CmdExe 内部解析为 authorId */
    private String authorUsername;
}
