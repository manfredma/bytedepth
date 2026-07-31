package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.UserAuthentication;
import manfred.bytedepth.app.user.UserAuthenticationPort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MyBatisUserAuthenticationAdapter implements UserAuthenticationPort {

    private final UserMapper userMapper;

    @Override
    public Optional<UserAuthentication> findByUsername(String username) {
        return Optional.ofNullable(userMapper.selectOne(
                new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username)))
                .map(user -> new UserAuthentication(
                        user.getId(),
                        user.getUsername(),
                        user.getPassword(),
                        user.getStatus(),
                        userMapper.selectPermissionCodesByUserId(user.getId())
                ));
    }
}
