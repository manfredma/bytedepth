package manfred.bytedepth.infrastructure.config;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MybatisPlusConfigTest {

    @Test
    void createsPaginationInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusConfig().mybatisPlusInterceptor();

        assertEquals(1, interceptor.getInterceptors().size());
    }
}
