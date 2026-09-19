package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.app.analytics.ViewLogArchiveSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ViewLogTablespaceMaintenanceMapper {

    ViewLogTablespaceState findState(@Param("source") ViewLogArchiveSource source);

    ViewLogTablespaceMetrics findMetrics(@Param("source") ViewLogArchiveSource source);

    int resetDeletedRows(@Param("source") ViewLogArchiveSource source);
}
