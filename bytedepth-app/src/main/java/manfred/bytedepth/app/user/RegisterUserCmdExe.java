package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterUserCmdExe {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void execute(String username, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new DomainException("用户名已存在：" + username);
        }
        validatePassword(rawPassword);
        User user = User.register(username, passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }

    /** 校验密码强度。 */
    private static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new DomainException("密码长度至少 8 位");
        }
        if (password.length() > 64) {
            throw new DomainException("密码长度不能超过 64 位");
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) hasLetter = true;
            else if (Character.isDigit(c)) hasDigit = true;
        }
        if (!hasLetter || !hasDigit) {
            throw new DomainException("密码必须包含字母和数字");
        }
    }
}
