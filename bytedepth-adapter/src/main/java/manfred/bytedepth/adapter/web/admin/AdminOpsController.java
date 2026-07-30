package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.ops.OpsOverviewDTO;
import manfred.bytedepth.app.ops.OpsOverviewQryExe;
import manfred.bytedepth.app.ops.OpsDeploymentStatusDTO;
import manfred.bytedepth.app.ops.OpsDeploymentStatusQryExe;
import manfred.bytedepth.app.ops.OpsTableDataDTO;
import manfred.bytedepth.app.ops.OpsTableQryExe;
import manfred.bytedepth.app.ops.RequestOpsDeploymentCmdExe;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
    private final OpsDeploymentStatusQryExe deploymentStatusQryExe;
    private final RequestOpsDeploymentCmdExe requestDeploymentCmdExe;

    public AdminOpsController(OpsOverviewQryExe overviewQryExe, OpsTableQryExe tableQryExe,
                              OpsDeploymentStatusQryExe deploymentStatusQryExe,
                              RequestOpsDeploymentCmdExe requestDeploymentCmdExe) {
        this.overviewQryExe = overviewQryExe;
        this.tableQryExe = tableQryExe;
        this.deploymentStatusQryExe = deploymentStatusQryExe;
        this.requestDeploymentCmdExe = requestDeploymentCmdExe;
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
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Operations data unavailable");
        }
    }

    @GetMapping("/api/deployment")
    @ResponseBody
    public OpsDeploymentStatusDTO deploymentStatus() {
        return deploymentStatusQryExe.execute();
    }

    @PostMapping("/api/deployment")
    @ResponseBody
    @PreAuthorize("hasAuthority('ops:monitor:view') and hasAuthority('ops:deploy:execute')")
    public OpsDeploymentStatusDTO deployMain() {
        return requestDeploymentCmdExe.execute();
    }
}
