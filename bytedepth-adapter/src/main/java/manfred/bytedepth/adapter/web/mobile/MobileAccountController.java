package manfred.bytedepth.adapter.web.mobile;

import java.util.List;
import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import manfred.bytedepth.app.user.RegisterUserCmdExe;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/v1")
public class MobileAccountController {
    private final RegisterUserCmdExe register;

    public MobileAccountController(RegisterUserCmdExe register) {
        this.register = register;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal SiteUserDetails principal) {
        return new MeResponse(
                principal.getId().toString(),
                principal.getUsername(),
                principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .sorted()
                        .toList());
    }

    @PostMapping("/registrations")
    public ResponseEntity<RegistrationResponse> register(@RequestBody RegistrationRequest request) {
        register.execute(request.username(), request.password());
        return ResponseEntity.accepted().body(new RegistrationResponse("PENDING"));
    }

    public record CsrfResponse(String headerName, String token) {}

    public record MeResponse(String id, String username, List<String> permissions) {}

    public record RegistrationRequest(String username, String password) {}

    public record RegistrationResponse(String status) {}
}
