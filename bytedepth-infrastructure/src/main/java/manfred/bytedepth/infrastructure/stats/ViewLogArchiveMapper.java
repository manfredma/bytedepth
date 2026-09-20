package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.app.analytics.ViewLogArchiveSource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ViewLogArchiveMapper {

    List<LocalDateTime> findCandidateBuckets(@Param("source") ViewLogArchiveSource source,
                                             @Param("cutoff") LocalDateTime cutoff,
                                             @Param("maxBuckets") int maxBuckets);

    ViewLogArchiveBucket findBucketState(@Param("source") ViewLogArchiveSource source,
                                         @Param("bucketStart") LocalDateTime bucketStart);

    long countRows(@Param("source") ViewLogArchiveSource source,
                   @Param("bucketStart") LocalDateTime bucketStart,
                   @Param("bucketEnd") LocalDateTime bucketEnd);

    int insertExactHourlyAggregates(@Param("source") ViewLogArchiveSource source,
                                    @Param("bucketStart") LocalDateTime bucketStart,
                                    @Param("bucketEnd") LocalDateTime bucketEnd);

    int insertExactCountryAggregates(@Param("source") ViewLogArchiveSource source,
                                     @Param("bucketStart") LocalDateTime bucketStart,
                                     @Param("bucketEnd") LocalDateTime bucketEnd);

    default int insertExactAggregates(ViewLogArchiveSource source,
                                      LocalDateTime bucketStart,
                                      LocalDateTime bucketEnd) {
        return insertExactHourlyAggregates(source, bucketStart, bucketEnd)
                + insertExactCountryAggregates(source, bucketStart, bucketEnd);
    }

    int incrementHourlyAggregates(@Param("source") ViewLogArchiveSource source,
                                  @Param("bucketStart") LocalDateTime bucketStart,
                                  @Param("bucketEnd") LocalDateTime bucketEnd);

    int incrementCountryAggregates(@Param("source") ViewLogArchiveSource source,
                                   @Param("bucketStart") LocalDateTime bucketStart,
                                   @Param("bucketEnd") LocalDateTime bucketEnd);

    default int incrementAggregates(ViewLogArchiveSource source,
                                    LocalDateTime bucketStart,
                                    LocalDateTime bucketEnd) {
        return incrementHourlyAggregates(source, bucketStart, bucketEnd)
                + incrementCountryAggregates(source, bucketStart, bucketEnd);
    }

    int deleteBucket(@Param("source") ViewLogArchiveSource source,
                     @Param("bucketStart") LocalDateTime bucketStart,
                     @Param("bucketEnd") LocalDateTime bucketEnd);

    int insertBucketState(@Param("source") ViewLogArchiveSource source,
                          @Param("bucketStart") LocalDateTime bucketStart,
                          @Param("deletedRows") long deletedRows);

    int updateBucketState(@Param("source") ViewLogArchiveSource source,
                          @Param("bucketStart") LocalDateTime bucketStart,
                          @Param("deletedRows") long deletedRows);

    int incrementTablespaceDeletedRows(@Param("source") ViewLogArchiveSource source,
                                       @Param("deletedRows") long deletedRows);
}
