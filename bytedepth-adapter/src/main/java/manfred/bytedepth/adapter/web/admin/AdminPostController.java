package manfred.bytedepth.adapter.web.admin;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmd;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import manfred.bytedepth.app.post.command.DeletePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.command.SetPostTagsCmdExe;
import manfred.bytedepth.app.post.command.UpdatePostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListAllPostsQryExe;
import manfred.bytedepth.app.series.AppendPostToSeriesCmdExe;
import manfred.bytedepth.app.series.RemovePostFromSeriesCmdExe;
import manfred.bytedepth.domain.common.SlugUtils;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@PreAuthorize("hasAuthority(\'admin:dashboard:view\')")
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
    private final ListCategoriesQryExe listCategoriesQryExe;
    private final SetPostTagsCmdExe setPostTagsCmdExe;
    private final SeriesRepository seriesRepository;
    private final AppendPostToSeriesCmdExe appendPostToSeriesCmdExe;
    private final RemovePostFromSeriesCmdExe removePostFromSeriesCmdExe;
    private final PostRepository postRepository;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "20") int size) {
        var result = listAllPostsQryExe.execute(page, size);
        int totalPages = (int) Math.ceil((double) result.total() / size);
        model.addAttribute("posts", result.posts());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("total", result.total());
        model.addAttribute("pageSize", size);
        model.addAttribute("allSeries", seriesRepository.findAll());
        return "admin/posts/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("cmd", new CreatePostCmd());
        model.addAttribute("categories", listCategoriesQryExe.execute());
        return "admin/posts/edit";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("post", getPostQryExe.execute(id));
        model.addAttribute("categories", listCategoriesQryExe.execute());
        return "admin/posts/edit";
    }

    @PostMapping
    public String create(@ModelAttribute CreatePostCmd cmd) {
        // 从 SecurityContext 取当前管理员用户名作为文章作者
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails ud) {
            cmd.setAuthorUsername(ud.getUsername());
        }
        createPostCmdExe.execute(cmd);
        return "redirect:/admin/posts";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String title,
                         @RequestParam String content,
                         @RequestParam(required = false) Long categoryId) {
        updatePostCmdExe.execute(id, title, content, categoryId);
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

    @PostMapping("/{id}/tags")
    @ResponseBody
    public ResponseEntity<Void> setTags(@PathVariable Long id,
                                        @RequestParam(required = false, defaultValue = "") List<String> tags) {
        setPostTagsCmdExe.execute(id, tags);
        return ResponseEntity.ok().build();
    }

    /**
     * 更新文章 slug。
     * 格式要求：仅含 [a-z0-9-]，首尾为英数字，无连续 -。
     * 返回 200 成功，400 格式非法，409 slug 已被其他文章占用。
     */
    @PostMapping("/{id}/slug")
    @ResponseBody
    public ResponseEntity<String> updateSlug(@PathVariable Long id,
                                             @RequestParam String slug) {
        if (!SlugUtils.isValid(slug)) {
            return ResponseEntity.badRequest()
                    .body("slug 格式非法：只允许小写英文、数字和连字符（-），首尾须为英数字，不可连续 -");
        }
        // 唯一性检查：同 slug 被其他文章占用则拒绝
        var conflict = postRepository.findBySlug(slug);
        if (conflict.isPresent() && !conflict.get().getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("slug '" + slug + "' 已被 postId=" + conflict.get().getId() + " 占用");
        }
        postRepository.updateSlug(id, slug);
        return ResponseEntity.ok("ok");
    }

    /** 文章列表页快速绑定专栏 */
    @PostMapping("/{id}/series/assign")
    public String assignSeries(@PathVariable Long id,
                               @RequestParam Long seriesId,
                               @RequestParam(defaultValue = "1") int page) {
        appendPostToSeriesCmdExe.execute(id, seriesId);
        return "redirect:/admin/posts?page=" + page;
    }

    /** 文章列表页移出专栏 */
    @PostMapping("/{id}/series/remove")
    public String removeSeries(@PathVariable Long id,
                               @RequestParam(defaultValue = "1") int page) {
        removePostFromSeriesCmdExe.execute(id);
        return "redirect:/admin/posts?page=" + page;
    }
}
