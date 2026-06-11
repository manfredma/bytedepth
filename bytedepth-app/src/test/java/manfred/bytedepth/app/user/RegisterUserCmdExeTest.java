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
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegisterUserCmdExeTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    private RegisterUserCmdExe exe;

    @BeforeEach
    void setUp() { exe = new RegisterUserCmdExe(userRepository, passwordEncoder); }

    @Test
    void execute_newUsername_savesUserAsPending() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("pass")).thenReturn("$2a$hash$");

        exe.execute("alice", "pass");

        verify(userRepository).save(argThat(u ->
            "alice".equals(u.getUsername()) && u.getStatus() == UserStatus.PENDING));
    }

    @Test
    void execute_duplicateUsername_throwsAndDoesNotSave() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(DomainException.class, () -> exe.execute("alice", "pass"));
        verify(userRepository, never()).save(any());
    }
}
