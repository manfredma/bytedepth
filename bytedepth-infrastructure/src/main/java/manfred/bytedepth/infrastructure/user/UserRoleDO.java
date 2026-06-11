package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_role")
public class UserRoleDO {
    private Long userId;
    private Long roleId;
}
