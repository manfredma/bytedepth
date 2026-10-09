package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class NetworkMapController {

    private final NetworkMapProperties networkMapProperties;

    @GetMapping("/network")
    public String network(Model model) {
        model.addAttribute("groups", networkMapProperties.getGroups());
        return "public/network";
    }
}
