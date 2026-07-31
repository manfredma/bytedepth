package manfred.bytedepth.app.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LoadUserAuthenticationQryExe {

    private final UserAuthenticationPort userAuthenticationPort;

    public Optional<UserAuthentication> execute(String username) {
        return userAuthenticationPort.findByUsername(username);
    }
}
