package manfred.bytedepth.adapter.web.portal;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmd;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
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
    private final MarkdownRenderer markdownRenderer;
    private final ListCommentsQryExe listCommentsQryExe;
    private final ListTagsQryExe listTagsQryExe;
    private final ListCategoriesQryExe listCategoriesQryExe;
    private final PostViewCounter postViewCounter;
    private final PostRepository postRepository;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String tag,
                       @RequestParam(required = false) String category) {
        var posts = (tag != null && !tag.isBlank())
                ? listPostsQryExe.executeByTag(tag, page, size)
                : (category != null && !category.isBlank())
                        ? listPostsQryExe.executeByCategory(category, page, size)
                        : listPostsQryExe.execute(page, size);
        model.addAttribute("posts", posts);
        model.addAttribute("currentPage", page);
        model.addAttribute("activeTag", tag);
        model.addAttribute("activeCategory", category);
        model.addAttribute("allTags", listTagsQryExe.findAllWithCount());
        model.addAttribute("allCategories", listCategoriesQryExe.execute());
        return "public/posts/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long id, Model model) {
        var post = getPostQryExe.execute(id);
        model.addAttribute("post", post);
        model.addAttribute("renderedContent", markdownRenderer.render(post.getContent()));
        model.addAttribute("tags", listTagsQryExe.findByPostId(id));
        model.addAttribute("comments", listCommentsQryExe.findApprovedByPostId(id));
        postViewCounter.increment(id);
        model.addAttribute("pvCount", postViewCounter.getCount(id));
        model.addAttribute("prevPost", postRepository.findPrevPublished(id).orElse(null));
        model.addAttribute("nextPost", postRepository.findNextPublished(id).orElse(null));
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
