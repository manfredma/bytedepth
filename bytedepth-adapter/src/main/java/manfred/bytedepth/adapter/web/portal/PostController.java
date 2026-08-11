package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.CsrfTokenInitializer;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.adapter.web.util.SecurityUtils;
import manfred.bytedepth.adapter.web.util.SeoUtils;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.adapter.web.util.WebUtils;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.app.annotation.PostAnnotationDTO;
import manfred.bytedepth.adapter.web.portal.AnnotationVisitorIdentity;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmd;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.rating.GetPostRatingQryExe;
import manfred.bytedepth.app.series.GetSeriesPostsQryExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;
import manfred.bytedepth.domain.stats.PostViewedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;

@Controller
@RequestMapping("/posts")
@RequiredArgsConstructor
public class PostController {

    @Value("${bytedepth.site.url}")
    private String siteUrl;

    private final ListPostsQryExe listPostsQryExe;
    private final GetPostQryExe getPostQryExe;
    private final CreatePostCmdExe createPostCmdExe;
    private final PublishPostCmdExe publishPostCmdExe;
    private final MarkdownRenderer markdownRenderer;
    private final ListCommentsQryExe listCommentsQryExe;
    private final ListAnnotationsQryExe listAnnotationsQryExe;
    private final AnnotationVisitorIdentity annotationVisitorIdentity;
    private final ListTagsQryExe listTagsQryExe;
    private final ListCategoriesQryExe listCategoriesQryExe;
    private final PostViewCounter postViewCounter;
    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;
    private final GetSeriesPostsQryExe getSeriesPostsQryExe;
    private final GetPostRatingQryExe getPostRatingQryExe;
    private final VisitRequestFilter visitRequestFilter;
    private final ApplicationEventPublisher eventPublisher;

    @GetMapping
    public String list(Model model,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "10") int size,
                       @RequestParam(required = false) String tag,
                       @RequestParam(required = false) String category) {
        var posts = tag != null && !tag.isBlank()
                ? listPostsQryExe.executeByTag(tag, page, size)
                : (category != null && !category.isBlank())
                        ? listPostsQryExe.executeByCategory(category, page, size)
                        : listPostsQryExe.execute(page, size);
        long total = tag != null && !tag.isBlank()
                ? listPostsQryExe.countByTag(tag)
                : (category != null && !category.isBlank())
                        ? listPostsQryExe.countByCategory(category)
                        : listPostsQryExe.countPublished();
        long totalPages = Math.max(1, (total + size - 1) / size);
        model.addAttribute("posts", posts);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("total", total);
        model.addAttribute("pageSize", size);
        model.addAttribute("hasPrev", page > 1);
        model.addAttribute("hasNext", page < totalPages);
        model.addAttribute("activeTag", tag);
        model.addAttribute("activeCategory", category);
        model.addAttribute("allTags", listTagsQryExe.findAllWithCount());
        model.addAttribute("allCategories", listCategoriesQryExe.execute());
        model.addAttribute("allSeries", seriesRepository.findAll());
        return "public/posts/list";
    }

    /**
     * 文章详情：支持 slug 和数字 ID 两种路径。
     * 数字 ID → 301 重定向到 slug URL（兼容旧链接、admin 后台跳转）。
     */
    @GetMapping("/{identifier}")
    public String detail(@PathVariable("identifier") String identifier,
                         Model model,
                         HttpServletRequest request) {
        CsrfTokenInitializer.initialize(request);
        // 纯数字 → 按 ID 查出 slug 后重定向
        if (identifier.matches("\\d+")) {
            var p = postRepository.findById(Long.parseLong(identifier))
                    .orElseThrow(() -> new NoSuchElementException("博文不存在：" + identifier));
            return "redirect:/posts/" + p.getSlug();
        }

        var post = getPostQryExe.executeBySlug(identifier);
        Long id = post.getId();
        UserDetails currentUser = SecurityUtils.currentUser();
        boolean isAdmin = SecurityUtils.hasAuthority(currentUser, "blog:post:manage");
        boolean isOwner = SecurityUtils.isOwner(currentUser, post.getAuthorId());
        boolean isDraft = "DRAFT".equals(post.getStatus());

        // 草稿仅作者或管理员可查看
        if (!"PUBLISHED".equals(post.getStatus()) && !isOwner && !isAdmin) {
            throw new NoSuchElementException();
        }

        model.addAttribute("post", post);
        model.addAttribute("metaDescription", SeoUtils.excerpt(post.getContent()));
        model.addAttribute("canonicalUrl", siteUrl + "/posts/" + post.getSlug());
        model.addAttribute("renderedContent", markdownRenderer.render(post.getContent()));
        model.addAttribute("wordCount", markdownRenderer.countVisibleCharacters(post.getContent()));
        model.addAttribute("tags", listTagsQryExe.findByPostId(id));
        model.addAttribute("comments", listCommentsQryExe.findApprovedByPostId(id));
        Long currentUserId = SecurityUtils.extractUserId(currentUser);
        String annotationOwnerTokenHash = annotationVisitorIdentity.existingHash(request);
        model.addAttribute("annotations", listAnnotationsQryExe.execute(id, currentUserId, annotationOwnerTokenHash).stream()
                .map(annotation -> PostAnnotationDTO.from(annotation, currentUserId, annotationOwnerTokenHash)).toList());
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("rating", getPostRatingQryExe.execute(id,
                WebUtils.readCookie(request, PostRatingController.VISITOR_COOKIE)));
        // The visibility check above has already established that a draft can reach this point
        // only for its owner or an administrator.
        model.addAttribute("canPublish", isDraft);
        String userAgent = WebUtils.truncate(request.getHeader("User-Agent"), 512);
        if (visitRequestFilter.shouldRecord(new VisitRequestFilter.Request(userAgent))) {
            String visitToken = UUID.randomUUID().toString();
            postViewCounter.increment(id);
            eventPublisher.publishEvent(new PostViewedEvent(
                    id,
                    SecurityUtils.extractUserId(currentUser),
                    WebUtils.getClientIp(request),
                    userAgent,
                    WebUtils.truncate(request.getHeader("Referer"), 512),
                    visitToken,
                    LocalDateTime.now()
            ));
            model.addAttribute("visitToken", visitToken);
        }
        model.addAttribute("pvCount", postViewCounter.getCount(id));

        model.addAttribute("prevPost", postRepository.findPrevPublished(id).orElse(null));
        model.addAttribute("nextPost", postRepository.findNextPublished(id).orElse(null));
        var currentPost = postRepository.findById(id).orElseThrow();
        if (currentPost.getSeriesId() != null) {
            model.addAttribute("series",
                seriesRepository.findById(currentPost.getSeriesId()).orElse(null));
            model.addAttribute("seriesPosts",
                getSeriesPostsQryExe.execute(currentPost.getSeriesId()));
        }
        return "public/posts/detail";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('blog:post:create')")
    public String newForm(Model model) {
        model.addAttribute("cmd", new CreatePostCmd());
        model.addAttribute("categories", listCategoriesQryExe.execute());
        return "admin/posts/edit";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('blog:post:create')")
    public String create(@ModelAttribute CreatePostCmd cmd) {
        UserDetails user = SecurityUtils.currentUser();
        if (user != null) {
            cmd.setAuthorUsername(user.getUsername());
        }
        Long id = createPostCmdExe.execute(cmd);
        // 用 slug 构建跳转 URL
        String slug = postRepository.findById(id).map(p -> p.getSlug()).orElse(id.toString());
        return "redirect:/posts/" + slug;
    }

    @PostMapping("/{slug}/publish")
    @PreAuthorize("isAuthenticated()")
    public String publish(@PathVariable("slug") String slug) {
        var post = getPostQryExe.executeBySlug(slug);
        UserDetails user = SecurityUtils.currentUser();
        if (!SecurityUtils.hasAuthority(user, "blog:post:manage")
                && !SecurityUtils.isOwner(user, post.getAuthorId())) {
            throw new AccessDeniedException("无权发布他人文章");
        }
        publishPostCmdExe.execute(post.getId());
        return "redirect:/posts/" + slug;
    }
}
