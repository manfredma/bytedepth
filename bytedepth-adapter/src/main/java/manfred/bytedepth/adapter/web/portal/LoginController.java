package manfred.bytedepth.adapter.web.portal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final boolean hearthEnabled;

    public LoginController(@Value("${bytedepth.hearth-oidc.enabled:false}") boolean hearthEnabled) {
        this.hearthEnabled = hearthEnabled;
    }

    @GetMapping("/login")
    public String login(
            @RequestParam(required = false) String oidc,
            @RequestParam(required = false) String error,
            @org.springframework.web.bind.annotation.CookieValue(name = "bytedepth-auth-logout", defaultValue = "")
                    String logout,
            @RequestParam(required = false) String interactive) {
        if (!hearthEnabled) {
            return "public/login";
        }
        // Account outcomes must not restart SSO: Hearth may already have a session.
        if (oidc != null || error != null) {
            return "public/authentication-result";
        }
        if ("1".equals(logout) && interactive == null) {
            return "public/authentication-required";
        }
        return "redirect:/oauth2/authorization/hearth";
    }

    @GetMapping(value = "/login", params = "required")
    public String required(
            jakarta.servlet.http.HttpServletRequest request,
            jakarta.servlet.http.HttpServletResponse response,
            org.springframework.security.core.Authentication authentication) {
        if (!hearthEnabled) {
            return "public/login";
        }
        if (!new org.springframework.security.authentication.AuthenticationTrustResolverImpl()
                .isAuthenticated(authentication)) {
            return "public/authentication-required";
        }
        var cache = new org.springframework.security.web.savedrequest.HttpSessionRequestCache();
        var saved = cache.getRequest(request, response);
        cache.removeRequest(request, response);
        if (saved != null) {
            var target = java.net.URI.create(saved.getRedirectUrl());
            var current = java.net.URI.create(request.getRequestURL().toString());
            if (current.getScheme().equals(target.getScheme())
                    && current.getAuthority().equals(target.getAuthority())
                    && !target.getPath().startsWith("//")
                    && !target.getPath().contains("\\")) {
                return "redirect:" + target.getRawPath()
                        + (target.getRawQuery() == null ? "" : "?" + target.getRawQuery());
            }
        }
        return "redirect:/";
    }
}
