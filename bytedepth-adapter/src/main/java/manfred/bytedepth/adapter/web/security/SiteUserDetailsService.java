package manfred.bytedepth.adapter.web.security;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.LoadUserAuthenticationQryExe;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SiteUserDetailsService implements UserDetailsService {

    private final LoadUserAuthenticationQryExe loadUserAuthenticationQryExe;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var user = loadUserAuthenticationQryExe.execute(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));
        if ("PENDING".equals(user.status())) {
            throw new DisabledException("账号待管理员审核，请耐心等待");
        }
        if ("BANNED".equals(user.status())) {
            throw new LockedException("账号已被封禁");
        }
        return new SiteUserDetails(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.permissionCodes().stream().map(SimpleGrantedAuthority::new).toList()
        );
    }
}
