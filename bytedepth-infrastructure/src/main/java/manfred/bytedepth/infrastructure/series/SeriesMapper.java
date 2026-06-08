package manfred.bytedepth.infrastructure.series;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeriesMapper extends BaseMapper<SeriesDO> {

    @Select("SELECT p.id, p.title, p.series_order, p.content, p.status, p.published_at " +
            "FROM post p WHERE p.series_id = #{seriesId} AND p.status = 'PUBLISHED' " +
            "ORDER BY p.series_order ASC")
    List<SeriesPostItemDO> findPublishedPostsBySeries(@Param("seriesId") Long seriesId);

    @Select("SELECT p.id, p.title, p.series_order, p.content, p.status, p.published_at " +
            "FROM post p WHERE p.series_id = #{seriesId} AND p.status != 'DELETED' " +
            "ORDER BY p.series_order ASC")
    List<SeriesPostItemDO> findAllPostsBySeries(@Param("seriesId") Long seriesId);

    @Select("<script>" +
            "SELECT p.id, p.title, p.series_order, p.content, p.status, p.published_at FROM post p " +
            "WHERE (p.series_id IS NULL OR p.series_id != #{seriesId}) " +
            "AND p.status != 'DELETED' " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND p.title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "ORDER BY p.created_at DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<SeriesPostItemDO> findCandidatesForSeries(@Param("seriesId") Long seriesId,
                                                   @Param("keyword") String keyword,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    @Select("<script>" +
            "SELECT COUNT(*) FROM post p " +
            "WHERE (p.series_id IS NULL OR p.series_id != #{seriesId}) " +
            "AND p.status != 'DELETED' " +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND p.title LIKE CONCAT('%', #{keyword}, '%') " +
            "</if>" +
            "</script>")
    long countCandidatesForSeries(@Param("seriesId") Long seriesId,
                                  @Param("keyword") String keyword);

    @Select("SELECT COALESCE(MAX(p.series_order), 0) FROM post p " +
            "WHERE p.series_id = #{seriesId} AND p.status != 'DELETED'")
    int findMaxOrderInSeries(@Param("seriesId") Long seriesId);
}
