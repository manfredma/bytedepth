package manfred.bytedepth.app.post.command;

import com.alibaba.cola.dto.Command;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CreatePostCmd extends Command {
    private String title;
    private String content;
}
