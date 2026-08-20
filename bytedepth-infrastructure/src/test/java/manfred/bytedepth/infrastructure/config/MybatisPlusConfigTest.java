package manfred.bytedepth.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    @Test
    void createsPaginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors()).singleElement()
                .isInstanceOfSatisfying(PaginationInnerInterceptor.class,
                        pagination -> assertThat(pagination.getDbType()).isEqualTo(DbType.MYSQL));
    }
}
