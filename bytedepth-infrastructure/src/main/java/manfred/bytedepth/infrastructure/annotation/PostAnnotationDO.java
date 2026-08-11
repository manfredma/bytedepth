package manfred.bytedepth.infrastructure.annotation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("post_annotation")
public class PostAnnotationDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private String ownerTokenHash;
    private String selectedText;
    private String annotationText;
    private String color;
    private String visibility;
    private Integer startOffset;
    private Integer endOffset;
    private LocalDateTime createdAt;
}
