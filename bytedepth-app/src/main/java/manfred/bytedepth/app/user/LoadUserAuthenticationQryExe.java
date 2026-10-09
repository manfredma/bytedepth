package manfred.bytedepth.app.user;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LoadUserAuthenticationQryExe {

    private final UserAuthenticationPort userAuthenticationPort;

    public Optional<UserAuthentication> execute(String username) {
        return userAuthenticationPort.findByUsername(username);
    }
}
