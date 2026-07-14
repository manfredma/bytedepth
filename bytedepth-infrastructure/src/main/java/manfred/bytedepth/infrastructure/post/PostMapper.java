package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface PostMapper extends BaseMapper<PostDO> {

    @Select("SELECT p.*, COALESCE(ps.pv_count, 0) AS view_count " +
            "FROM post p " +
            "LEFT JOIN page_stats ps ON ps.path = CONCAT('/posts/', p.id) " +
            "WHERE p.status = 'PUBLISHED' " +
            "ORDER BY view_count DESC, p.published_at DESC, p.id DESC " +
            "LIMIT #{offset}, #{limit}")
    List<HotPostDO> findPublishedByHotness(@Param("offset") int offset,
                                           @Param("limit") int limit);

    @Select({"<script>",
            "SELECT p.* FROM post p WHERE p.status = 'PUBLISHED'",
            "<if test='excludedIds != null and !excludedIds.isEmpty()'>",
            "AND p.id NOT IN",
            "<foreach item='id' collection='excludedIds' open='(' separator=',' close=')'>",
            "#{id}",
            "</foreach>",
            "</if>",
            "ORDER BY p.published_at DESC, p.id DESC LIMIT #{limit}",
            "</script>"})
    List<PostDO> findLatestPublishedExcluding(@Param("excludedIds") List<Long> excludedIds,
                                              @Param("limit") int limit);

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

    @Update("UPDATE post SET slug = #{slug} WHERE id = #{id}")
    void updateSlug(@Param("id") Long id, @Param("slug") String slug);
}
