package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.ops.OpsOverviewDTO;
import manfred.bytedepth.app.ops.OpsOverviewQryExe;
import manfred.bytedepth.app.ops.OpsTableDataDTO;
import manfred.bytedepth.app.ops.OpsTableQryExe;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** Read-only operations monitoring page and its JSON endpoints. */
@Controller
@RequestMapping("/admin/ops")
@PreAuthorize("hasAuthority('ops:monitor:view')")
public class AdminOpsController {

    private final OpsOverviewQryExe overviewQryExe;
    private final OpsTableQryExe tableQryExe;

    public AdminOpsController(OpsOverviewQryExe overviewQryExe, OpsTableQryExe tableQryExe) {
        this.overviewQryExe = overviewQryExe;
        this.tableQryExe = tableQryExe;
    }

    @GetMapping
    public String page() {
        return "admin/ops/dashboard";
    }

    @GetMapping("/api/overview")
    @ResponseBody
    public OpsOverviewDTO overview() {
        return overviewQryExe.execute();
    }

    @GetMapping("/api/tables/{tableName}")
    @ResponseBody
    public OpsTableDataDTO table(@PathVariable String tableName) {
        try {
            return tableQryExe.execute(tableName);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported operations table");
        }
    }
}
