package manfred.bytedepth.infrastructure.user;

import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.OidcIdentityAuthenticationPort;
import manfred.bytedepth.app.user.UserAuthentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MyBatisOidcIdentityAuthenticationAdapter implements OidcIdentityAuthenticationPort {

    private final UserMapper userMapper;
    private final OidcIdentityMapper identityMapper;

    @Override
    @Transactional
    public Optional<UserAuthentication> authenticate(
            String issuer, String subject, String preferredUsername, String email) {
        OidcIdentityDO identity = identityMapper.selectByIssuerAndSubject(issuer, subject);
        UserDO user;
        if (identity == null) {
            user = newUser(issuer, subject, preferredUsername, email);
            userMapper.insert(user);
            identity = new OidcIdentityDO();
            identity.setUserId(user.getId());
            identity.setIssuer(issuer);
            identity.setSubject(subject);
            identityMapper.insert(identity);
        } else {
            user = userMapper.selectById(identity.getUserId());
        }
        if (user == null) return Optional.empty();
        return Optional.of(new UserAuthentication(
                user.getId(),
                user.getUsername(),
                user.getPassword(),
                user.getStatus(),
                userMapper.selectPermissionCodesByUserId(user.getId())));
    }

    private UserDO newUser(String issuer, String subject, String preferredUsername, String email) {
        String base =
                preferredUsername == null || preferredUsername.isBlank() ? "hearth-user" : preferredUsername.trim();
        base = base.substring(0, Math.min(base.length(), 40));
        String username = base;
        if (userMapper.selectByUsername(username) != null) {
            String suffix = Integer.toHexString((issuer + "\n" + subject).hashCode());
            username = base.substring(0, Math.min(base.length(), 50 - suffix.length() - 1)) + "-" + suffix;
        }
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword("");
        user.setEmail(email);
        user.setStatus("PENDING");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return user;
    }
}
