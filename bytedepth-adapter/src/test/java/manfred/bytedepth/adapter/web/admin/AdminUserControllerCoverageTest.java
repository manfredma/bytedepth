package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.user.ActivateUserCmdExe;
import manfred.bytedepth.app.user.BanUserCmdExe;
import manfred.bytedepth.app.user.ListPendingUsersQryExe;
import manfred.bytedepth.domain.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminUserControllerCoverageTest {

    private final ListPendingUsersQryExe users = mock(ListPendingUsersQryExe.class);
    private final AdminUserController controller = new AdminUserController(users, mock(ActivateUserCmdExe.class),
            mock(BanUserCmdExe.class), mock(UserRepository.class));

    @Test
    void list_coversUsernameAndAllStatusFilterVariants() {
        when(users.findPage(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new ListPendingUsersQryExe.UserPageResult(List.of(), 0));

        assertEquals("admin/users/list", controller.list(new ConcurrentModel(), null, null, 1, 20));
        assertEquals("admin/users/list", controller.list(new ConcurrentModel(), " ", "PENDING", 1, 20));
        assertEquals("admin/users/list", controller.list(new ConcurrentModel(), "alice", "ACTIVE", 1, 20));
        assertEquals("admin/users/list", controller.list(new ConcurrentModel(), null, "BANNED", 1, 20));
        assertEquals("admin/users/list", controller.list(new ConcurrentModel(), null, " ", 1, 20));
    }
}
