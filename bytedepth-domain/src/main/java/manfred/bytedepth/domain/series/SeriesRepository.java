package manfred.bytedepth.domain.series;

import java.util.List;
import java.util.Optional;

public interface SeriesRepository {
    Series save(Series series);
    Optional<Series> findBySlug(String slug);
    Optional<Series> findById(Long id);
    /** 按 name ASC 排序 */
    List<Series> findAll();
    /** 查询某位作者的专栏，按 name ASC 排序。 */
    List<Series> findByAuthorId(Long authorId);
    List<Series> findPage(String name, int page, int size);
    long count(String name);
    List<Series> findPageByAuthorId(Long authorId, String name, int page, int size);
    long countByAuthorId(Long authorId, String name);
    List<SeriesPostItem> findPublishedPostsBySeries(Long seriesId);
    /** 查询专栏下所有文章（含草稿），按 series_order ASC，后台管理用 */
    List<SeriesPostItem> findAllPostsBySeries(Long seriesId);
    /** 查询可加入专栏的候选文章（尚未加入该专栏的已发布/草稿文章），按 created_at DESC */
    List<SeriesPostItem> findCandidatesForSeries(Long seriesId, String keyword, int page, int size);
    long countCandidatesForSeries(Long seriesId, String keyword);
    List<SeriesPostItem> findCandidatesForSeriesByAuthor(Long seriesId, Long authorId, String keyword, int page, int size);
    long countCandidatesForSeriesByAuthor(Long seriesId, Long authorId, String keyword);
    /** 查询专栏当前最大 series_order，无文章时返回 0 */
    int findMaxOrderInSeries(Long seriesId);
    /** 删除专栏，并清除所有关联文章的 series_id/series_order */
    void deleteWithPosts(Long seriesId);
}
