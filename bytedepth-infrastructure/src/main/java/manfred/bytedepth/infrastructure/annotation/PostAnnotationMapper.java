package manfred.bytedepth.infrastructure.annotation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostAnnotationMapper extends BaseMapper<PostAnnotationDO> {

    @Select("""
      <script>
      SELECT * FROM post_annotation
      WHERE post_id = #{postId}
        AND ((anchor_json IS NOT NULL AND deleted = 0 AND visibility = 'PUBLIC')
             <if test="userId != null">OR (user_id = #{userId} AND (deleted = 0 OR anchor_json IS NULL))</if>
             <if test="ownerTokenHash != null and ownerTokenHash != ''">OR (owner_token_hash = #{ownerTokenHash} AND (deleted = 0 OR anchor_json IS NULL))</if>)
      ORDER BY start_offset ASC, id ASC
      </script>
      """)
    List<PostAnnotationDO> findVisibleByPostId(
            @Param("postId") Long postId, @Param("userId") Long userId, @Param("ownerTokenHash") String ownerTokenHash);

    @Update(
            "UPDATE post_annotation SET annotation_text=#{annotationText}, color=#{color}, visibility=#{visibility} WHERE id=#{id}")
    int updateMutableFields(
            @Param("id") long id,
            @Param("annotationText") String annotationText,
            @Param("color") String color,
            @Param("visibility") String visibility);

    @Update(
            "UPDATE post_annotation SET anchor_json=#{anchorJson}, migration_baseline_version=#{baseline} WHERE id=#{id} AND anchor_json IS NULL AND migration_baseline_version IS NULL")
    int initializeAnchor(
            @Param("id") long id, @Param("anchorJson") String anchorJson, @Param("baseline") long baseline);
}
