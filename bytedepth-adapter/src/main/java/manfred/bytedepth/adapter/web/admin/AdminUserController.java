package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.ActivateUserCmdExe;
import manfred.bytedepth.app.user.BanUserCmdExe;
import manfred.bytedepth.app.user.ListPendingUsersQryExe;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.adapter.web.filter.FilterField;
import manfred.bytedepth.adapter.web.filter.FilterOption;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriUtils;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Controller
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final ListPendingUsersQryExe listPendingUsersQryExe;
    private final ActivateUserCmdExe activateUserCmdExe;
    private final BanUserCmdExe banUserCmdExe;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('system:user:approve')")
    public String list(Model model, @RequestParam(required = false) String username,
                       @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        var result = listPendingUsersQryExe.findPage(username, status, page, size);
        model.addAttribute("users", result.users()); model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", (int) Math.ceil((double) result.total() / size)); model.addAttribute("total", result.total()); model.addAttribute("pageSize", size);
        model.addAttribute("filterFields", List.of(
                FilterField.text("username", "用户名", username == null ? "" : username, "输入用户名"),
                FilterField.select("status", "状态", status == null ? "" : status, List.of(
                        FilterOption.of("", "全部"),
                        FilterOption.of("PENDING", "待审核", "PENDING".equals(status)),
                        FilterOption.of("ACTIVE", "已激活", "ACTIVE".equals(status)),
                        FilterOption.of("BANNED", "已封禁", "BANNED".equals(status))))));
        String base = "/admin/users?" + (username == null || username.isBlank() ? "" : "username=" + UriUtils.encodeQueryParam(username, StandardCharsets.UTF_8) + "&") + (status == null || status.isBlank() ? "" : "status=" + status + "&");
        model.addAttribute("filterBaseUrl", base);
        return "admin/users/list";
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('system:user:approve')")
    public String activate(@PathVariable Long id) {
        activateUserCmdExe.execute(id);
        return "redirect:/admin/users";
    }

    /** 拒绝注册：直接删除 PENDING 记录 */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('system:user:approve')")
    public String deletePending(@PathVariable Long id) {
        userRepository.deleteById(id);
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/ban")
    @PreAuthorize("hasAuthority('system:user:manage')")
    public String ban(@PathVariable Long id) {
        banUserCmdExe.execute(id);
        return "redirect:/admin/users";
    }
}
