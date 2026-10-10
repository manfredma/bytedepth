package manfred.bytedepth.infrastructure.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class PostMapperSqlContractTest {

    @Test
    void latestPublishedExcluding_ordersByUpdatedAt() throws NoSuchMethodException {
        Method method = PostMapper.class.getMethod("findLatestPublishedExcluding", java.util.List.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("ORDER BY p.updated_at DESC, p.id DESC");
        assertThat(sql).doesNotContain("ORDER BY p.published_at DESC");
    }

    @Test
    void taggedPublishedPosts_orderByUpdatedAt() throws NoSuchMethodException {
        Method method = PostMapper.class.getMethod("findPublishedByTagSlug", String.class, int.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains("ORDER BY p.updated_at DESC, p.id DESC");
        assertThat(sql).doesNotContain("ORDER BY p.published_at DESC");
    }

    @Test
    void discoveryPosts_useRecentWeightedViewsBeforeHistoricalViews() throws NoSuchMethodException {
        Method method = PostMapper.class.getMethod(
                "findPublishedByDiscoveryExcluding", java.util.List.class, int.class, int.class);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql)
                .contains("post_view_log")
                .contains("INTERVAL 7 DAY")
                .contains("POW(0.5")
                .contains("recent_score")
                .contains("COALESCE(ps.pv_count, 0) * 0.05")
                .contains("ORDER BY discovery_score DESC, view_count DESC, p.published_at DESC, p.id DESC");
    }
}
