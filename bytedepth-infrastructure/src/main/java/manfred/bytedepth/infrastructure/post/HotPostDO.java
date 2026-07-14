package manfred.bytedepth.infrastructure.post;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class HotPostDO extends PostDO {
    private Long viewCount;
}
