package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostViewLogMapper extends BaseMapper<PostViewLogDO> {

    @Select("""
            SELECT pvl.*, p.title AS post_title
            FROM post_view_log pvl
            LEFT JOIN post p ON p.id = pvl.post_id
            WHERE (#{postId} IS NULL OR pvl.post_id = #{postId})
              AND (#{userId} IS NULL OR pvl.user_id = #{userId})
            ORDER BY pvl.visited_at DESC
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
