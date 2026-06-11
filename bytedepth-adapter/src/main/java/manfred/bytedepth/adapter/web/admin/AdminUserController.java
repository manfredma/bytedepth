package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.ActivateUserCmdExe;
import manfred.bytedepth.app.user.BanUserCmdExe;
import manfred.bytedepth.app.user.ListPendingUsersQryExe;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
    public String list(Model model) {
        model.addAttribute("pendingUsers", listPendingUsersQryExe.execute());
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
