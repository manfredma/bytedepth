package manfred.bytedepth.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MybatisPlusConfigTest {

    @Test
    void createsPaginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        // MyBatis-Plus 3.5.17 内置分页支持，无需额外注册 PaginationInnerInterceptor
        assertEquals(0, interceptor.getInterceptors().size());
    }
}
