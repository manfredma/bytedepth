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
    public String login(@RequestParam(required = false) String oidc, @RequestParam(required = false) String error) {
        if (!hearthEnabled) {
            return "public/login";
        }
        // Account outcomes must not restart SSO: Hearth may already have a session.
        if (oidc != null || error != null) {
            return "public/authentication-result";
        }
        return "redirect:/oauth2/authorization/hearth";
    }
}
