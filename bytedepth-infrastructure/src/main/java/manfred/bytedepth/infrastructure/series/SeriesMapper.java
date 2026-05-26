package manfred.bytedepth.infrastructure.series;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SeriesMapper extends BaseMapper<SeriesDO> {

    @Select("SELECT p.id, p.title, p.series_order FROM post p " +
            "WHERE p.series_id = #{seriesId} AND p.status = 'PUBLISHED' " +
            "ORDER BY p.series_order ASC")
    List<SeriesPostItemDO> findPublishedPostsBySeries(@Param("seriesId") Long seriesId);
}
