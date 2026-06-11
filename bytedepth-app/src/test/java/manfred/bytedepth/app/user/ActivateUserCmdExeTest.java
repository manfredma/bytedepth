package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivateUserCmdExeTest {

    @Mock private UserRepository userRepository;
    private ActivateUserCmdExe exe;

    @BeforeEach
    void setUp() { exe = new ActivateUserCmdExe(userRepository); }

    @Test
    void execute_pendingUser_savesActiveAndAssignsRole() {
        User user = User.register("bob", "hash");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        exe.execute(1L);

        verify(userRepository).save(argThat(u -> u.getStatus() == UserStatus.ACTIVE));
        verify(userRepository).assignRole(1L, "USER");
    }

    @Test
    void execute_userNotFound_throwsDomainException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(DomainException.class, () -> exe.execute(99L));
    }
}
