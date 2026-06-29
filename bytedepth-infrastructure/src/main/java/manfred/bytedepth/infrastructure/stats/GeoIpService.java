package manfred.bytedepth.infrastructure.stats;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;

/**
 * 基于 MaxMind GeoLite2 离线库的 IP 地理位置解析服务。
 * 数据库文件路径通过 {@code bytedepth.geoip.db-path} 配置。
 * 文件缺失或加载失败时降级：{@link #resolve(String)} 一律返回 {@link GeoInfo#unknown()}。
 */
@Slf4j
@Service
public class GeoIpService {

    private final String dbPath;
    private DatabaseReader reader;

    public GeoIpService(@Value("${bytedepth.geoip.db-path:}") String dbPath) {
        this.dbPath = dbPath;
    }

    @PostConstruct
    void init() {
        if (dbPath == null || dbPath.isBlank()) {
            log.warn("GeoIP: bytedepth.geoip.db-path 未配置，IP 地理解析已禁用");
            return;
        }
        File file = new File(dbPath);
        if (!file.exists()) {
            log.warn("GeoIP: 数据库文件不存在：{}，IP 地理解析已禁用", dbPath);
            return;
        }
        try {
            reader = new DatabaseReader.Builder(file).build();
            log.info("GeoIP: 数据库加载成功：{}", dbPath);
        } catch (IOException e) {
            log.warn("GeoIP: 数据库加载失败：{}，IP 地理解析已禁用", dbPath, e);
        }
    }

    /**
     * 解析 IP 地址的国家和城市。
     * 任何异常均静默降级，返回 {@link GeoInfo#unknown()}。
     */
    public GeoInfo resolve(String ip) {
        if (reader == null || ip == null || ip.isBlank()) {
            return GeoInfo.unknown();
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            var response = reader.city(addr);
            String country = response.getCountry().getName();
            String city = response.getCity().getName();
            return new GeoInfo(
                country == null ? "" : country,
                city == null ? "" : city
            );
        } catch (IOException | GeoIp2Exception e) {
            log.debug("GeoIP: 解析失败 ip={}：{}", ip, e.getMessage());
            return GeoInfo.unknown();
        } catch (Exception e) {
            log.debug("GeoIP: 意外异常 ip={}：{}", ip, e.getMessage());
            return GeoInfo.unknown();
        }
    }
}
