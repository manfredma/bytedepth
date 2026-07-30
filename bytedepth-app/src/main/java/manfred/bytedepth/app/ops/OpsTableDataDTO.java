package manfred.bytedepth.app.ops;

import java.util.List;
import java.util.Map;

public record OpsTableDataDTO(String tableName, List<String> columns,
                               List<Map<String, Object>> rows) {

    public OpsTableDataDTO {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }
}
