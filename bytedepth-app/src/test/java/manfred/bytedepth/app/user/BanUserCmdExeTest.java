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
class BanUserCmdExeTest {

    @Mock private UserRepository userRepository;
    private BanUserCmdExe exe;

    @BeforeEach
    void setUp() { exe = new BanUserCmdExe(userRepository); }

    @Test
    void execute_activeUser_savesBanned() {
        User user = User.register("carol", "hash");
        user.activate();
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));

        exe.execute(2L);

        verify(userRepository).save(argThat(u -> u.getStatus() == UserStatus.BANNED));
    }

    @Test
    void execute_userNotFound_throwsDomainException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(DomainException.class, () -> exe.execute(99L));
    }
}
