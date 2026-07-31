package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.adapter.web.util.SeoUtils;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.adapter.web.util.CsrfTokenInitializer;
import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmd;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.rating.GetPostRatingQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.series.GetSeriesPostsQryExe;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;
import manfred.bytedepth.domain.stats.PostViewedEvent;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
        var posts = (tag != null && !tag.isBlank())
                ? listPostsQryExe.executeByTag(tag, page, size)
                : (category != null && !category.isBlank())
                        ? listPostsQryExe.executeByCategory(category, page, size)
                        : listPostsQryExe.execute(page, size);
        long total = (tag != null && !tag.isBlank())
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
        UserDetails currentUser = currentUser();
        boolean isAdmin = hasAuthority(currentUser, "blog:post:manage");
        boolean isOwner = isOwner(currentUser, post.getAuthorId());

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
        model.addAttribute("rating", getPostRatingQryExe.execute(id, readRatingVisitorToken(request)));
        model.addAttribute("canPublish", "DRAFT".equals(post.getStatus()) && (isOwner || isAdmin));
        String userAgent = truncate(request.getHeader("User-Agent"), 512);
        if (visitRequestFilter.shouldRecord(new VisitRequestFilter.Request(userAgent))) {
            String visitToken = UUID.randomUUID().toString();
            postViewCounter.increment(id);
            eventPublisher.publishEvent(new PostViewedEvent(
                    id,
                    extractUserId(currentUser),
                    getClientIp(request),
                    userAgent,
                    truncate(request.getHeader("Referer"), 512),
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

    private String readRatingVisitorToken(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (PostRatingController.VISITOR_COOKIE.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
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
        UserDetails user = currentUser();
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
        UserDetails user = currentUser();
        if (!hasAuthority(user, "blog:post:manage") && !isOwner(user, post.getAuthorId())) {
            throw new AccessDeniedException("无权发布他人文章");
        }
        publishPostCmdExe.execute(post.getId());
        return "redirect:/posts/" + slug;
    }

    // ── 辅助：从 SecurityContextHolder 读取当前用户 ────────────────────────────

    private UserDetails currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        Object principal = auth.getPrincipal();
        return (principal instanceof UserDetails ud) ? ud : null;
    }

    private boolean hasAuthority(UserDetails user, String authority) {
        return user != null && user.getAuthorities().stream()
            .anyMatch(a -> authority.equals(a.getAuthority()));
    }

    private boolean isOwner(UserDetails user, Long authorId) {
        if (user == null || authorId == null) return false;
        if (user instanceof SiteUserDetails sd) {
            return sd.getId().equals(authorId);
        }
        return false;
    }

    /** 从登录用户中提取数据库 ID，匿名返回 null。 */
    private Long extractUserId(UserDetails user) {
        if (user instanceof SiteUserDetails sd) {
            return sd.getId();
        }
        return null;
    }

    /**
     * 获取客户端真实 IP：优先取 X-Forwarded-For 首个非私有 IP，
     * 无法解析时回退到 RemoteAddr。
     */
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            for (String part : xff.split(",")) {
                String ip = part.trim();
                if (!ip.isBlank() && !isPrivateIp(ip)) {
                    return ip;
                }
            }
        }
        return request.getRemoteAddr();
    }

    /** 判断是否为私有/回环 IP（简单字符串匹配）。 */
    private boolean isPrivateIp(String ip) {
        return ip.startsWith("10.") || ip.startsWith("172.") || ip.startsWith("192.168.")
                || ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1");
    }

    /** 截断字符串至指定长度，null 安全。 */
    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
