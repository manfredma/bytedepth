package manfred.bytedepth.infrastructure.post;

import lombok.Data;

@Data
public class PostContentRevisionDO {
    private Long postId;
    private Long contentVersion;
    private String markdown;
    private String documentJson;
    private Boolean baseline;
}
