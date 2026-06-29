package manfred.bytedepth.infrastructure.stats;

/**
 * IP 地理位置解析结果。
 * 解析失败时返回 {@link #unknown()}（country、city 均为空字符串）。
 */
public record GeoInfo(String country, String city) {

    public static GeoInfo unknown() {
        return new GeoInfo("", "");
    }
}
