package manfred.bytedepth.infrastructure.series;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesPostItem;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class SeriesRepositoryImpl implements SeriesRepository {

    private final SeriesMapper seriesMapper;

    @Override
    public Series save(Series series) {
        SeriesDO seriesDO = toDO(series);
        if (series.getId() == null) {
            seriesMapper.insert(seriesDO);
        } else {
            seriesMapper.updateById(seriesDO);
        }
        return toEntity(seriesDO);
    }

    @Override
    public Optional<Series> findBySlug(String slug) {
        return Optional.ofNullable(seriesMapper.selectOne(
                new LambdaQueryWrapper<SeriesDO>().eq(SeriesDO::getSlug, slug)
        )).map(this::toEntity);
    }

    @Override
    public Optional<Series> findById(Long id) {
        return Optional.ofNullable(seriesMapper.selectById(id)).map(this::toEntity);
    }

    @Override
    public List<SeriesPostItem> findPublishedPostsBySeries(Long seriesId) {
        return seriesMapper.findPublishedPostsBySeries(seriesId).stream()
                .map(this::toSeriesPostItem)
                .collect(Collectors.toList());
    }

    @Override
    public List<SeriesPostItem> findAllPostsBySeries(Long seriesId) {
        return seriesMapper.findAllPostsBySeries(seriesId).stream()
                .map(this::toSeriesPostItem)
                .collect(Collectors.toList());
    }

    @Override
    public List<SeriesPostItem> findCandidatesForSeries(Long seriesId, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        return seriesMapper.findCandidatesForSeries(seriesId, keyword, offset, size).stream()
                .map(this::toSeriesPostItem)
                .collect(Collectors.toList());
    }

    private SeriesPostItem toSeriesPostItem(SeriesPostItemDO d) {
        return new SeriesPostItem(d.getId(), d.getSlug(), d.getTitle(), d.getSeriesOrder(),
                d.getContent(), d.getStatus(), d.getPublishedAt());
    }

    @Override
    public long countCandidatesForSeries(Long seriesId, String keyword) {
        return seriesMapper.countCandidatesForSeries(seriesId, keyword);
    }

    @Override
    public List<SeriesPostItem> findCandidatesForSeriesByAuthor(Long seriesId, Long authorId, String keyword, int page, int size) {
        return seriesMapper.findCandidatesForSeriesByAuthor(seriesId, authorId, keyword, (page - 1) * size, size).stream()
                .map(this::toSeriesPostItem).collect(Collectors.toList());
    }

    @Override
    public long countCandidatesForSeriesByAuthor(Long seriesId, Long authorId, String keyword) {
        return seriesMapper.countCandidatesForSeriesByAuthor(seriesId, authorId, keyword);
    }

    @Override
    public int findMaxOrderInSeries(Long seriesId) {
        return seriesMapper.findMaxOrderInSeries(seriesId);
    }

    @Override
    public void deleteWithPosts(Long seriesId) {
        // 先清除所有关联文章的 series_id/series_order，再删除专栏
        seriesMapper.clearAllPostsInSeries(seriesId);
        seriesMapper.deleteById(seriesId);
    }

    @Override
    public List<Series> findAll() {
        return seriesMapper.selectList(
                new LambdaQueryWrapper<SeriesDO>().orderByAsc(SeriesDO::getName)
        ).stream().map(this::toEntity).collect(Collectors.toList());
    }

    @Override
    public List<Series> findByAuthorId(Long authorId) {
        return seriesMapper.selectList(
                new LambdaQueryWrapper<SeriesDO>()
                        .eq(SeriesDO::getAuthorId, authorId)
                        .orderByAsc(SeriesDO::getName)
        ).stream().map(this::toEntity).collect(Collectors.toList());
    }

    private SeriesDO toDO(Series series) {
        SeriesDO d = new SeriesDO();
        d.setId(series.getId());
        d.setName(series.getName());
        d.setSlug(series.getSlug());
        d.setDescription(series.getDescription());
        d.setAuthorId(series.getAuthorId());
        return d;
    }

    private Series toEntity(SeriesDO d) {
        return Series.reconstruct(d.getId(), d.getName(), d.getSlug(), d.getDescription(), d.getAuthorId());
    }
}
