package manfred.bytedepth.infrastructure.category;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("category")
public class CategoryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String slug;
    private Long parentId;
}
