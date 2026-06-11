package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.user.GetUserProfileQryExe;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/u")
@RequiredArgsConstructor
public class UserProfileController {

    private final GetUserProfileQryExe getUserProfileQryExe;

    @GetMapping("/{username}")
    public String profile(@PathVariable String username, Model model) {
        try {
            model.addAttribute("profile", getUserProfileQryExe.execute(username));
            return "public/profile";
        } catch (DomainException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
