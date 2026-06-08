package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<PostDO> {

    @Select("SELECT p.* FROM post p " +
            "INNER JOIN post_tag pt ON p.id = pt.post_id " +
            "INNER JOIN tag t ON pt.tag_id = t.id " +
            "WHERE p.status = 'PUBLISHED' AND t.slug = #{slug} " +
            "ORDER BY p.published_at DESC " +
            "LIMIT #{offset}, #{limit}")
    List<PostDO> findPublishedByTagSlug(@Param("slug") String slug,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM post p " +
            "INNER JOIN post_tag pt ON p.id = pt.post_id " +
            "INNER JOIN tag t ON pt.tag_id = t.id " +
            "WHERE p.status = 'PUBLISHED' AND t.slug = #{slug}")
    long countPublishedByTagSlug(@Param("slug") String slug);

    @Select("SELECT * FROM post WHERE status = 'PUBLISHED' " +
            "AND published_at < (SELECT published_at FROM post WHERE id = #{id}) " +
            "ORDER BY published_at DESC LIMIT 1")
    PostDO findPrevPublished(@Param("id") Long id);

    @Select("SELECT * FROM post WHERE status = 'PUBLISHED' " +
            "AND published_at > (SELECT published_at FROM post WHERE id = #{id}) " +
            "ORDER BY published_at ASC LIMIT 1")
    PostDO findNextPublished(@Param("id") Long id);

    @Update("UPDATE post SET series_id = NULL, series_order = NULL WHERE id = #{postId}")
    void clearPostSeries(@Param("postId") Long postId);
}
