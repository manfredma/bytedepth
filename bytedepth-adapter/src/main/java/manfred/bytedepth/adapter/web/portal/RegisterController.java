package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.RegisterUserCmdExe;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Controller
@RequiredArgsConstructor
public class RegisterController {

    private final RegisterUserCmdExe registerUserCmdExe;

    @GetMapping("/register")
    public String showForm() {
        return "public/register";
    }

    @PostMapping("/register")
    public String submit(@RequestParam String username,
                         @RequestParam String password) {
        try {
            registerUserCmdExe.execute(username, password);
            return "redirect:/login?registered=1";
        } catch (DomainException e) {
            String encoded = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            return "redirect:/register?error=" + encoded;
        }
    }
}
