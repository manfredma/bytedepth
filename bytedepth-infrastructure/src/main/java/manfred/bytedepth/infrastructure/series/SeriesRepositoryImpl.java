package manfred.bytedepth.infrastructure.series;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.series.Series;
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
    public List<Series> findAll() {
        return seriesMapper.selectList(null).stream().map(this::toEntity).collect(Collectors.toList());
    }

    private SeriesDO toDO(Series series) {
        SeriesDO d = new SeriesDO();
        d.setId(series.getId());
        d.setName(series.getName());
        d.setSlug(series.getSlug());
        d.setDescription(series.getDescription());
        return d;
    }

    private Series toEntity(SeriesDO d) {
        return Series.reconstruct(d.getId(), d.getName(), d.getSlug(), d.getDescription());
    }
}
