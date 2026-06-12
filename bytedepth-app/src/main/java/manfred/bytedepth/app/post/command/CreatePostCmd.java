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
    /**
     * 文章 slug（仅含 [a-z0-9-]）。
     * 不填则由 CmdExe 根据标题自动生成；管理后台手动录入时由用户指定。
     */
    private String slug;
    /** 发文章的用户名（由 Controller 从 SecurityContext 取得），CmdExe 内部解析为 authorId */
    private String authorUsername;
}
