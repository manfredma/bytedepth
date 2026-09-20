package manfred.bytedepth.infrastructure.stats;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class PostViewLogMapperSqlContractTest {

    @Test
    void annotationQueriesUseNativeComparisonOperators() throws NoSuchMethodException {
        assertNativeGreaterThanOrEqual("findPage", Long.class, Long.class,
                java.time.LocalDateTime.class, int.class, int.class);
        assertNativeGreaterThanOrEqual("countPage", Long.class, Long.class,
                java.time.LocalDateTime.class);
    }

    private void assertNativeGreaterThanOrEqual(String methodName, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Method method = PostViewLogMapper.class.getMethod(methodName, parameterTypes);
        String sql = String.join(" ", method.getAnnotation(Select.class).value());

        assertThat(sql).contains(">=");
        assertThat(sql).doesNotContain("&gt;=");
    }
}
