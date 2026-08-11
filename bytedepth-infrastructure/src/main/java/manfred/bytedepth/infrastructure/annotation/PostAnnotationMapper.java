package manfred.bytedepth.infrastructure.annotation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PostAnnotationMapper extends BaseMapper<PostAnnotationDO> {

    @Select("""
            SELECT * FROM post_annotation
            WHERE post_id = #{postId}
            ORDER BY start_offset ASC, id ASC
            """)
    List<PostAnnotationDO> findByPostId(@Param("postId") Long postId);
}
