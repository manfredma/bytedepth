package manfred.bytedepth.app.user;

import java.util.Optional;

/** Outbound port for loading the data required by the web authentication adapter. */
public interface UserAuthenticationPort {

    Optional<UserAuthentication> findByUsername(String username);
}
