package manfred.bytedepth.infrastructure.user;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserRoleMapper {

    /**
     * user_role 使用复合主键，MyBatis-Plus 的 BaseMapper 只支持单一 @TableId，
     * 因而以显式插入语句表达这个关联表操作，避免生成不可用的 xxById 方法。
     */
    @Insert("INSERT INTO user_role (user_id, role_id) VALUES (#{userId}, #{roleId})")
    int insert(UserRoleDO userRole);
}
