package manfred.bytedepth.infrastructure.annotation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostAnnotationMapper extends BaseMapper<PostAnnotationDO> {

    @Select("""
            <script>
            SELECT * FROM post_annotation
            WHERE post_id = #{postId}
              AND (visibility = 'PUBLIC'
                   <if test="userId != null">OR user_id = #{userId}</if>
                   <if test="ownerTokenHash != null and ownerTokenHash != ''">OR owner_token_hash = #{ownerTokenHash}</if>)
            ORDER BY start_offset ASC, id ASC
            </script>
            """)
    List<PostAnnotationDO> findVisibleByPostId(@Param("postId") Long postId,
                                               @Param("userId") Long userId,
                                               @Param("ownerTokenHash") String ownerTokenHash);
}
