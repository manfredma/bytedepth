package manfred.bytedepth.app.ops;

import org.springframework.stereotype.Component;

@Component
public class OpsTableQryExe {

    private final OpsTableDataPort tableDataPort;

    public OpsTableQryExe(OpsTableDataPort tableDataPort) {
        this.tableDataPort = tableDataPort;
    }

    public OpsTableDataDTO execute(String tableName) {
        return tableDataPort.list(OpsTable.fromName(tableName));
    }
}
