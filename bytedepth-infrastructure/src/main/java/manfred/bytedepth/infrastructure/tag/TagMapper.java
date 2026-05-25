package manfred.bytedepth.infrastructure.tag;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<TagDO> {

    @Select("SELECT t.* FROM tag t INNER JOIN post_tag pt ON t.id = pt.tag_id WHERE pt.post_id = #{postId}")
    List<TagDO> findByPostId(Long postId);

    @Delete("DELETE FROM post_tag WHERE post_id = #{postId}")
    void deletePostTags(Long postId);

    @Insert("<script>INSERT IGNORE INTO post_tag (post_id, tag_id) VALUES " +
            "<foreach collection='tagIds' item='tagId' separator=','>(#{postId}, #{tagId})</foreach></script>")
    void insertPostTags(Long postId, List<Long> tagIds);
}
