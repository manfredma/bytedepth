package manfred.bytedepth.app.user;

import java.util.Optional;

/** Resolves an external OIDC identity to the business system's local user and permissions. */
public interface OidcIdentityAuthenticationPort {

    Optional<UserAuthentication> authenticate(String issuer, String subject, String preferredUsername, String email);
}
