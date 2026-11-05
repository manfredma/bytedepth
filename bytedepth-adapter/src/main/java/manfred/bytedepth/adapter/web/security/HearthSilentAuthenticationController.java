package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public final class HearthSilentAuthenticationController {
    private final HearthSilentAuthentication silent = new HearthSilentAuthentication();

    @GetMapping("/auth/status")
    @ResponseBody
    public Map<String, Object> status(
            HttpServletRequest request,
            HttpServletResponse response,
            CsrfToken csrf,
            @RequestParam(defaultValue = "false") boolean prepare) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of(
                "authenticated",
                silent.authenticated(),
                "suppressed",
                silent.suppressed(request),
                "interactive",
                silent.interactive(request),
                "csrfToken",
                prepare ? csrf.getToken() : "");
    }

    @PostMapping("/auth/silent")
    @ResponseBody
    public ResponseEntity<Map<String, String>> start(HttpServletRequest request) {
        if (!silent.begin(request)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of("start", "/oauth2/authorization/hearth?silent=true"));
    }

    @GetMapping("/auth/silent-complete")
    public String complete(@RequestParam(defaultValue = "anonymous") String outcome, Model model) {
        model.addAttribute(
                "outcome",
                Set.of("success", "anonymous", "pending", "blocked").contains(outcome) ? outcome : "anonymous");
        return "security/authentication-complete";
    }
}
