package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.dashboard.DashboardStatsQryExe;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@PreAuthorize("hasAuthority(\'admin:dashboard:view\')")
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardStatsQryExe dashboardStatsQryExe;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", dashboardStatsQryExe.execute());
        return "admin/dashboard";
    }
}
