package manfred.bytedepth.infrastructure.rating;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

@Mapper
public interface PostRatingMapper extends BaseMapper<PostRatingDO> {

    @Insert("""
            INSERT INTO post_rating (post_id, visitor_token, score, created_at, updated_at)
            VALUES (#{postId}, #{visitorToken}, #{score}, NOW(), NOW())
            ON DUPLICATE KEY UPDATE score = VALUES(score), updated_at = NOW()
            """)
    void upsert(@Param("postId") Long postId,
                @Param("visitorToken") String visitorToken,
                @Param("score") int score);

    @Select("""
            SELECT COALESCE(AVG(score), 0) AS average_rating, COUNT(*) AS rating_count
            FROM post_rating WHERE post_id = #{postId}
            """)
    PostRatingStatsDO findStats(@Param("postId") Long postId);

    @Select("SELECT score FROM post_rating WHERE post_id = #{postId} AND visitor_token = #{visitorToken}")
    Optional<Integer> findScore(@Param("postId") Long postId, @Param("visitorToken") String visitorToken);
}
