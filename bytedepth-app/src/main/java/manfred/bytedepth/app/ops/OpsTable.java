package manfred.bytedepth.app.ops;

import java.util.Arrays;
import java.util.List;

/** The only database tables that the operations page may inspect. */
public enum OpsTable {
    POST("post", List.of("id", "title", "status", "author_id", "created_at", "updated_at")),
    COMMENT("comment", List.of("id", "post_id", "author_id", "content", "created_at")),
    USER("user", List.of("id", "username", "email", "status", "created_at", "updated_at"));

    private final String tableName;
    private final List<String> columns;

    OpsTable(String tableName, List<String> columns) {
        this.tableName = tableName;
        this.columns = columns;
    }

    public String tableName() {
        return tableName;
    }

    public List<String> columns() {
        return columns;
    }

    public static OpsTable fromName(String tableName) {
        return Arrays.stream(values())
                .filter(table -> table.tableName.equals(tableName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported operations table"));
    }
}
