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
        User user = User.register(username, passwordEncoder.encode(rawPassword));
        userRepository.save(user);
    }
}
