package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.OidcIdentityAuthenticationPort;
import manfred.bytedepth.app.user.UserAuthentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;

@RequiredArgsConstructor
public final class HearthOidcAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final OidcIdentityAuthenticationPort identities;
    private final SecurityContextRepository securityContextRepository;
    private final SavedRequestAwareAuthenticationSuccessHandler redirect =
            new SavedRequestAwareAuthenticationSuccessHandler();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException, ServletException {
        if (!(authentication instanceof OAuth2AuthenticationToken token)
                || !(token.getPrincipal() instanceof OidcUser oidcUser)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Hearth OIDC principal is invalid");
            return;
        }
        String issuer =
                Optional.ofNullable(oidcUser.getIssuer()).map(Object::toString).orElse("");
        var local = identities
                .authenticate(
                        issuer,
                        oidcUser.getSubject(),
                        oidcUser.getClaimAsString("preferred_username"),
                        oidcUser.getClaimAsString("email"))
                .orElseThrow(() -> new IllegalStateException("Hearth identity has no local user"));
        if ("PENDING".equals(local.status())) {
            clearContext(request, response);
            response.sendRedirect("/login?oidc=pending");
            return;
        }
        if ("BANNED".equals(local.status())) {
            clearContext(request, response);
            response.sendRedirect("/login?oidc=blocked");
            return;
        }
        SiteUserDetails principal = toPrincipal(local);
        var localAuthentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        // Keep the verified protocol identity only for RP-Initiated Logout.
        // Business authorization continues to use the local principal/authorities.
        localAuthentication.setDetails(token);
        SecurityContextHolder.getContext().setAuthentication(localAuthentication);
        securityContextRepository.saveContext(SecurityContextHolder.getContext(), request, response);
        redirect.onAuthenticationSuccess(request, response, localAuthentication);
    }

    private void clearContext(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        securityContextRepository.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
    }

    private static SiteUserDetails toPrincipal(UserAuthentication user) {
        return new SiteUserDetails(
                user.id(),
                user.username(),
                user.passwordHash(),
                user.permissionCodes().stream().map(SimpleGrantedAuthority::new).toList());
    }
}
