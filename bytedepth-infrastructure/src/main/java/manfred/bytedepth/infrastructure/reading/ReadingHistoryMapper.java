package manfred.bytedepth.infrastructure.reading;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReadingHistoryMapper {
    int insertIfAbsent(ReadingEventDO event);

    List<ReadingEventDO> findUnprojected(@Param("limit") int limit);

    int markProjected(@Param("id") long id, @Param("projectedAt") LocalDateTime projectedAt);

    int deleteProjectedBefore(@Param("cutoff") LocalDateTime cutoff);

    int upsertHistory(ReadingEventDO event);

    ReadingHistoryDO findSummary(@Param("userId") long userId, @Param("postId") long postId);

    List<ReadingHistoryDO> findPage(
            @Param("userId") long userId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorPostId") Long cursorPostId,
            @Param("limit") int limit);
}
