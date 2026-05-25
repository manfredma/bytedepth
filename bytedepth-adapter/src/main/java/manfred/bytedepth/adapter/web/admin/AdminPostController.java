package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.post.command.CreatePostCmd;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.DeletePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.command.UpdatePostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListAllPostsQryExe;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/posts")
@RequiredArgsConstructor
public class AdminPostController {

    private final ListAllPostsQryExe listAllPostsQryExe;
    private final GetPostQryExe getPostQryExe;
    private final CreatePostCmdExe createPostCmdExe;
    private final UpdatePostCmdExe updatePostCmdExe;
    private final PublishPostCmdExe publishPostCmdExe;
    private final DeletePostCmdExe deletePostCmdExe;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        model.addAttribute("posts", listAllPostsQryExe.execute(page, size));
        model.addAttribute("currentPage", page);
        return "admin/posts/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("cmd", new CreatePostCmd());
        return "admin/posts/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("post", getPostQryExe.execute(id));
        return "admin/posts/edit";
    }

    @PostMapping
    public String create(@ModelAttribute CreatePostCmd cmd) {
        createPostCmdExe.execute(cmd);
        return "redirect:/admin/posts";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String title,
                         @RequestParam String content) {
        updatePostCmdExe.execute(id, title, content);
        return "redirect:/admin/posts";
    }

    @PostMapping("/{id}/publish")
    public String publish(@PathVariable Long id) {
        publishPostCmdExe.execute(id);
        return "redirect:/admin/posts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        deletePostCmdExe.execute(id);
        return "redirect:/admin/posts";
    }
}
