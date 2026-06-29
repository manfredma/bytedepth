package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostViewLogMapper extends BaseMapper<PostViewLogDO> {

    @Select("""
            SELECT *
            FROM post_view_log
            WHERE (#{postId} IS NULL OR post_id = #{postId})
              AND (#{userId} IS NULL OR user_id = #{userId})
            ORDER BY visited_at DESC
            LIMIT #{offset}, #{size}
            """)
    List<PostViewLogDO> findPage(@Param("postId") Long postId,
                                  @Param("userId") Long userId,
                                  @Param("offset") int offset,
                                  @Param("size") int size);

    @Select("""
            SELECT COUNT(*)
            FROM post_view_log
            WHERE (#{postId} IS NULL OR post_id = #{postId})
              AND (#{userId} IS NULL OR user_id = #{userId})
            """)
    long countPage(@Param("postId") Long postId,
                   @Param("userId") Long userId);
}
