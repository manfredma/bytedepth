package manfred.bytedepth.app.ops;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OpsTableDataDTO(String tableName, List<String> columns,
                               List<Map<String, Object>> rows) {

    public OpsTableDataDTO {
        List<String> allowedColumns = List.copyOf(columns);
        columns = allowedColumns;
        rows = rows.stream()
                .map(row -> {
                    Map<String, Object> allowedValues = new LinkedHashMap<>();
                    for (String column : allowedColumns) {
                        allowedValues.put(column, row.get(column));
                    }
                    return Collections.unmodifiableMap(allowedValues);
                })
                .toList();
    }
}
