package manfred.bytedepth.app.series;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeriesNavigationQryExeTest {

    private final SeriesNavigationQryExe query = new SeriesNavigationQryExe();

    @Test
    void middleArticleUsesAdjacentItemsInSeriesOrder() {
        var result = query.execute(9L, 2L, List.of(
                item(3L, "third", "第三篇", 3),
                item(1L, "first", "第一篇", 1),
                item(2L, "second", "第二篇", 2)
        ));

        assertThat(result.position()).isEqualTo(2);
        assertThat(result.total()).isEqualTo(3);
        assertThat(result.progressPercent()).isEqualTo(67);
        assertThat(result.previous().getId()).isEqualTo(1L);
        assertThat(result.next().getId()).isEqualTo(3L);
        assertThat(result.posts()).extracting(SeriesPostItemDTO::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void firstAndLastArticlesUseNullAtTheirOutsideBoundary() {
        var posts = List.of(item(1L, "first", "第一篇", 1), item(2L, "last", "末篇", 2));

        var first = query.execute(9L, 1L, posts);
        var last = query.execute(9L, 2L, posts);

        assertThat(first.position()).isEqualTo(1);
        assertThat(first.previous()).isNull();
        assertThat(first.next().getId()).isEqualTo(2L);
        assertThat(last.position()).isEqualTo(2);
        assertThat(last.previous().getId()).isEqualTo(1L);
        assertThat(last.next()).isNull();
    }

    @Test
    void singleArticleHasFullProgressAndNoAdjacentItems() {
        var result = query.execute(9L, 1L, List.of(item(1L, "only", "唯一篇", 7)));

        assertThat(result.position()).isEqualTo(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.progressPercent()).isEqualTo(100);
        assertThat(result.previous()).isNull();
        assertThat(result.next()).isNull();
    }

    @Test
    void equalSeriesOrderUsesIdAsStableTieBreaker() {
        var result = query.execute(9L, 5L, List.of(
                item(8L, "eight", "八", 2),
                item(5L, "five", "五", 2),
                item(3L, "three", "三", 1)
        ));

        assertThat(result.posts()).extracting(SeriesPostItemDTO::getId).containsExactly(3L, 5L, 8L);
        assertThat(result.previous().getId()).isEqualTo(3L);
        assertThat(result.next().getId()).isEqualTo(8L);
    }

    @Test
    void absentCurrentArticleHasNoAdjacentItemsAndZeroProgress() {
        var result = query.execute(9L, 99L, List.of(item(1L, "first", "第一篇", 1)));

        assertThat(result.position()).isZero();
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.progressPercent()).isZero();
        assertThat(result.previous()).isNull();
        assertThat(result.next()).isNull();
    }

    @Test
    void emptyOrNullSourceProducesAnEmptyNavigationModel() {
        var result = query.execute(null, null, null);

        assertThat(result.posts()).isEmpty();
        assertThat(result.total()).isZero();
        assertThat(result.position()).isZero();
        assertThat(result.progressPercent()).isZero();
        assertThat(result.previous()).isNull();
        assertThat(result.next()).isNull();
    }

    @Test
    void nullCurrentArticleDoesNotMatchPublishedItems() {
        var result = query.execute(9L, null, List.of(item(1L, "first", "第一篇", 1)));

        assertThat(result.position()).isZero();
        assertThat(result.progressPercent()).isZero();
        assertThat(result.previous()).isNull();
        assertThat(result.next()).isNull();
    }

    @Test
    void nullOrderAndIdAreSortedAfterStableItems() {
        var nullOrder = item(2L, "null-order", "无序号", null);
        var nullOrderAndId = item(null, "null-id", "无 ID", null);
        var result = query.execute(9L, 2L, List.of(
                nullOrderAndId,
                nullOrder,
                item(1L, "first", "第一篇", 1)
        ));

        assertThat(result.posts()).containsExactly(item(1L, "first", "第一篇", 1), nullOrder, nullOrderAndId);
        assertThat(result.position()).isEqualTo(2);
        assertThat(result.previous().getId()).isEqualTo(1L);
        assertThat(result.next().getId()).isNull();
    }

    private static SeriesPostItemDTO item(Long id, String slug, String title, Integer order) {
        SeriesPostItemDTO item = new SeriesPostItemDTO();
        item.setId(id);
        item.setSlug(slug);
        item.setTitle(title);
        item.setSeriesOrder(order);
        return item;
    }
}
