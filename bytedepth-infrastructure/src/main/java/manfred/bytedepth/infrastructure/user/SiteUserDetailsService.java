package manfred.bytedepth.infrastructure.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SiteUserDetailsService implements UserDetailsService {

    private final UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserDO user = userMapper.selectOne(
            new LambdaQueryWrapper<UserDO>().eq(UserDO::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在：" + username);
        }
        if ("PENDING".equals(user.getStatus())) {
            throw new DisabledException("账号待管理员审核，请耐心等待");
        }
        if ("BANNED".equals(user.getStatus())) {
            throw new LockedException("账号已被封禁");
        }
        List<SimpleGrantedAuthority> authorities =
            userMapper.selectPermissionCodesByUserId(user.getId())
                .stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new SiteUserDetails(
            user.getId(), user.getUsername(), user.getPassword(), authorities);
    }
}
