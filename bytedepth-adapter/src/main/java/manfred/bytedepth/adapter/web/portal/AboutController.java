package manfred.bytedepth.adapter.web.portal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 关于页面 — 静态介绍网站内容，无需查库。
 */
@Controller
public class AboutController {

    @GetMapping("/about")
    public String about() {
        return "public/about";
    }
}
