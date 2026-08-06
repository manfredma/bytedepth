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
        when(passwordEncoder.encode("Pass1word")).thenReturn("$2a$hash$");

        exe.execute("alice", "Pass1word");

        verify(userRepository).save(argThat(u ->
            "alice".equals(u.getUsername()) && u.getStatus() == UserStatus.PENDING));
    }

    @Test
    void execute_duplicateUsername_throwsAndDoesNotSave() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThrows(DomainException.class, () -> exe.execute("alice", "Pass1word"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void execute_tooShortPassword_throws() {
        assertThrows(DomainException.class, () -> exe.execute("u", "Ab1"),
                "密码长度至少 8 位");
    }

    @Test
    void execute_nullPassword_throws() {
        assertThrows(DomainException.class, () -> exe.execute("u", null),
                "密码长度至少 8 位");
    }

    @Test
    void execute_noLetterPassword_throws() {
        assertThrows(DomainException.class, () -> exe.execute("u", "1234567890"),
                "密码必须包含字母和数字");
    }

    @Test
    void execute_noDigitPassword_throws() {
        assertThrows(DomainException.class, () -> exe.execute("u", "Abcdefghij"),
                "密码必须包含字母和数字");
    }

    @Test
    void execute_tooLongPassword_throws() {
        String longPwd = "Ab1" + "a".repeat(62);
        assertThrows(DomainException.class, () -> exe.execute("u", longPwd),
                "密码长度不能超过 64 位");
    }
}
