package manfred.bytedepth.infrastructure.post;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

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
}
