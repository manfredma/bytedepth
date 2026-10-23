package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface OidcIdentityMapper extends BaseMapper<OidcIdentityDO> {

    @Select("SELECT * FROM user_identity WHERE issuer = #{issuer} AND subject = #{subject}")
    OidcIdentityDO selectByIssuerAndSubject(String issuer, String subject);
}
