package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import manfred.bytedepth.app.analytics.PostViewLogDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostViewLogMapper extends BaseMapper<PostViewLogDO> {

    @Insert("""
            INSERT INTO post_view_log (post_id, user_id, ip, user_agent, referer, country, city, visited_at, visit_token)
            VALUES (#{postId}, #{userId}, #{ip}, #{userAgent}, #{referer}, #{country}, #{city}, #{visitedAt}, #{visitToken})
            ON DUPLICATE KEY UPDATE
                user_id = VALUES(user_id), ip = VALUES(ip), user_agent = VALUES(user_agent),
                referer = VALUES(referer), country = VALUES(country), city = VALUES(city), visited_at = VALUES(visited_at)
            """)
    int upsertVisit(PostViewLogDO log);

    @Insert("""
            INSERT INTO post_view_log (post_id, visited_at, visit_token, active_read_seconds, max_scroll_depth, last_activity_at, completed_at)
            VALUES (#{postId}, NOW(), #{visitToken}, #{activeReadSeconds}, #{maxScrollDepth}, NOW(),
                    CASE WHEN #{completed} THEN NOW() ELSE NULL END)
            ON DUPLICATE KEY UPDATE
                active_read_seconds = GREATEST(active_read_seconds, VALUES(active_read_seconds)),
                max_scroll_depth = GREATEST(max_scroll_depth, VALUES(max_scroll_depth)),
                last_activity_at = VALUES(last_activity_at),
                completed_at = CASE WHEN VALUES(completed_at) IS NOT NULL AND completed_at IS NULL THEN VALUES(completed_at) ELSE completed_at END
            """)
    int upsertReadingProgress(@Param("postId") Long postId,
                              @Param("visitToken") String visitToken,
                              @Param("activeReadSeconds") int activeReadSeconds,
                              @Param("maxScrollDepth") int maxScrollDepth,
                              @Param("completed") boolean completed);

    @Select("""
            SELECT pvl.*, p.title AS post_title
            FROM post_view_log pvl
            LEFT JOIN post p ON p.id = pvl.post_id
            WHERE (#{postId} IS NULL OR pvl.post_id = #{postId})
              AND (#{userId} IS NULL OR pvl.user_id = #{userId})
            ORDER BY pvl.visited_at DESC
            LIMIT #{offset}, #{size}
            """)
    List<PostViewLogDTO> findPage(@Param("postId") Long postId,
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
