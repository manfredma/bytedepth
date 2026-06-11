package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BanUserCmdExe {

    private final UserRepository userRepository;

    public void execute(Long userId) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new DomainException("用户不存在：" + userId));
        user.ban();
        userRepository.save(user);
    }
}
