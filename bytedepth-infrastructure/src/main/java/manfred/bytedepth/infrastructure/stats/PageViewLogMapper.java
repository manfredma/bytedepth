package manfred.bytedepth.infrastructure.stats;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PageViewLogMapper extends BaseMapper<PageViewLogDO> {

    @Insert("""
            INSERT INTO page_view_log (page_path, user_id, ip, user_agent, referer, country, city, visited_at)
            VALUES (#{pagePath}, #{userId}, #{ip}, #{userAgent}, #{referer}, #{country}, #{city}, #{visitedAt})
            """)
    int insertLog(PageViewLogDO log);
}
