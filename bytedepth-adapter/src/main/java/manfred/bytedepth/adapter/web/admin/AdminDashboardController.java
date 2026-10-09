package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.dashboard.DashboardStatsQryExe;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@PreAuthorize("hasAnyAuthority('admin:dashboard:view', 'blog:post:create', 'blog:series:create:own')")
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardStatsQryExe dashboardStatsQryExe;

    @GetMapping
    public String dashboard(Authentication authentication, Model model) {
        boolean canViewDashboard = authentication.getAuthorities().stream()
                .anyMatch(authority -> "admin:dashboard:view".equals(authority.getAuthority()));
        if (!canViewDashboard) {
            return "redirect:/admin/posts";
        }
        model.addAttribute("stats", dashboardStatsQryExe.execute());
        return "admin/dashboard";
    }
}
