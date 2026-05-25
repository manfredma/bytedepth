package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.command.CreatePostCmd;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    private final ListPostsQryExe listPostsQryExe;
    private final GetPostQryExe getPostQryExe;
    private final CreatePostCmdExe createPostCmdExe;
    private final PublishPostCmdExe publishPostCmdExe;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size) {
        model.addAttribute("posts", listPostsQryExe.execute(page, size));
        model.addAttribute("currentPage", page);
        return "public/posts/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("post", getPostQryExe.execute(id));
        return "public/posts/detail";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("cmd", new CreatePostCmd());
        return "admin/posts/edit";
    }

    @PostMapping
    public String create(@ModelAttribute CreatePostCmd cmd) {
        Long id = createPostCmdExe.execute(cmd);
        return "redirect:/posts/" + id;
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable("id") Long id) {
        publishPostCmdExe.execute(id);
        return "redirect:/posts/" + id;
    }
}
