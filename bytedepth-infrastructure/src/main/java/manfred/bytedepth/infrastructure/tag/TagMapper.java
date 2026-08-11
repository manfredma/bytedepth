package manfred.bytedepth.infrastructure.tag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<TagDO> {

    @Select("SELECT t.* FROM tag t INNER JOIN post_tag pt ON t.id = pt.tag_id WHERE pt.post_id = #{postId}")
    List<TagDO> findByPostId(Long postId);

    @Delete("DELETE FROM post_tag WHERE post_id = #{postId}")
    void deletePostTags(Long postId);

    @Delete("DELETE FROM post_tag WHERE tag_id = #{tagId}")
    void deletePostTagAssociations(Long tagId);

    @Insert("<script>INSERT IGNORE INTO post_tag (post_id, tag_id) VALUES " +
            "<foreach collection='tagIds' item='tagId' separator=','>(#{postId}, #{tagId})</foreach></script>")
    void insertPostTags(Long postId, List<Long> tagIds);

    @Select("SELECT t.id, t.name, t.slug, COUNT(pt.post_id) AS post_count " +
            "FROM tag t LEFT JOIN post_tag pt ON t.id = pt.tag_id " +
            "LEFT JOIN post p ON pt.post_id = p.id AND p.status = 'PUBLISHED' " +
            "GROUP BY t.id, t.name, t.slug ORDER BY post_count DESC")
    List<TagWithCountDO> findAllWithCount();

    @Select("<script>SELECT t.id, t.name, t.slug, COUNT(p.id) AS post_count " +
            "FROM tag t LEFT JOIN post_tag pt ON t.id = pt.tag_id " +
            "LEFT JOIN post p ON pt.post_id = p.id AND p.status = 'PUBLISHED' " +
            "<where><if test='name != null and name != \"\"'>AND t.name LIKE CONCAT('%', #{name}, '%')</if></where> " +
            "GROUP BY t.id, t.name, t.slug ORDER BY post_count DESC LIMIT #{offset}, #{size}</script>")
    List<TagWithCountDO> findPageWithCount(@Param("name") String name, @Param("offset") int offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM tag t " +
            "<where><if test='name != null and name != \"\"'>AND t.name LIKE CONCAT('%', #{name}, '%')</if></where></script>")
    long countWithName(@Param("name") String name);
}
