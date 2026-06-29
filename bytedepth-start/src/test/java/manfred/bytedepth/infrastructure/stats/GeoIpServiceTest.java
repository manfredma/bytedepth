package manfred.bytedepth.infrastructure.stats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GeoIpServiceTest {

    private GeoIpService geoIpService;

    @BeforeEach
    void setUp() {
        // dbPath 为空字符串 → 降级模式（文件不存在），所有 resolve() 返回 unknown
        geoIpService = new GeoIpService("");
    }

    @Test
    void resolve_whenDbNotLoaded_returnsUnknown() {
        GeoInfo info = geoIpService.resolve("8.8.8.8");
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }

    @Test
    void resolve_privateIp_returnsUnknown() {
        GeoInfo info = geoIpService.resolve("192.168.1.1");
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }

    @Test
    void resolve_blankIp_returnsUnknown() {
        GeoInfo info = geoIpService.resolve("");
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }

    @Test
    void resolve_nullIp_returnsUnknown() {
        GeoInfo info = geoIpService.resolve(null);
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }

    @Test
    void resolve_malformedIp_returnsUnknown() {
        GeoInfo info = geoIpService.resolve("not-an-ip");
        assertThat(info.country()).isEmpty();
        assertThat(info.city()).isEmpty();
    }
}
