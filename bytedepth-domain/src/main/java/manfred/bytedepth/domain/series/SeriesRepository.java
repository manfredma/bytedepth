package manfred.bytedepth.domain.series;

import java.util.List;
import java.util.Optional;

public interface SeriesRepository {
    Series save(Series series);
    Optional<Series> findBySlug(String slug);
    Optional<Series> findById(Long id);
    List<Series> findAll();
    List<SeriesPostItem> findPublishedPostsBySeries(Long seriesId);
}
