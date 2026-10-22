package manfred.bytedepth.infrastructure.annotation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PostAnnotationMapper extends BaseMapper<PostAnnotationDO> {
    // Apply access and owner predicates before LIMIT; filtering an all-row list would corrupt paging.
    // Legacy deleted flags remain diagnostic, matching the existing owner-visible migration policy.
    @Select(
            "SELECT a.* FROM post_annotation a JOIN post p ON p.id=a.post_id WHERE a.user_id=#{userId} AND p.status='PUBLISHED' AND (a.deleted=0 OR a.anchor_json IS NULL) ORDER BY a.created_at DESC,a.id DESC LIMIT #{size} OFFSET #{offset}")
    List<PostAnnotationDO> findOwnPublished(
            @Param("userId") Long userId, @Param("offset") long offset, @Param("size") int size);

    @Select(
            "SELECT COUNT(*) FROM post_annotation a JOIN post p ON p.id=a.post_id WHERE a.user_id=#{userId} AND p.status='PUBLISHED' AND (a.deleted=0 OR a.anchor_json IS NULL)")
    long countOwnPublished(@Param("userId") Long userId);

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
