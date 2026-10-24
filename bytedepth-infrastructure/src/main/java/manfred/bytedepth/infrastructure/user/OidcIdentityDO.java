package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("user_identity")
public class OidcIdentityDO {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String issuer;
    private String subject;
}
