package manfred.bytedepth.infrastructure.ops;

import java.util.LinkedHashMap;
import java.util.Map;

final class RedisInfoParser {

    private RedisInfoParser() {
    }

    static Map<String, String> parse(String info) {
        Map<String, String> values = new LinkedHashMap<>();
        if (info == null || info.isBlank()) {
            return values;
        }
        for (String line : info.split("\\r?\\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf(':');
            if (separator > 0) {
                values.put(line.substring(0, separator), line.substring(separator + 1));
            }
        }
        return values;
    }

    static long longValue(Map<String, String> info, String key) {
        try {
            return Long.parseLong(info.getOrDefault(key, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
