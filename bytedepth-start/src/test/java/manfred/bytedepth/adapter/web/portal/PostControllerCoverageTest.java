package manfred.bytedepth.adapter.web.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import manfred.bytedepth.adapter.web.util.MarkdownRenderer;
import manfred.bytedepth.adapter.web.util.SecurityUtils;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.adapter.web.util.WebUtils;
import manfred.bytedepth.app.category.ListCategoriesQryExe;
import manfred.bytedepth.app.comment.ListCommentsQryExe;
import manfred.bytedepth.app.post.command.CreatePostCmd;
import manfred.bytedepth.app.post.command.CreatePostCmdExe;
import manfred.bytedepth.app.post.command.PublishPostCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.ListPostsQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.rating.GetPostRatingQryExe;
import manfred.bytedepth.app.series.GetSeriesPostsQryExe;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.domain.stats.PostViewCounter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;

class PostControllerCoverageTest {

    private final ListPostsQryExe listPosts = mock(ListPostsQryExe.class);
    private final GetPostQryExe getPost = mock(GetPostQryExe.class);
    private final CreatePostCmdExe createPost = mock(CreatePostCmdExe.class);
    private final PublishPostCmdExe publishPost = mock(PublishPostCmdExe.class);
    private final ListCategoriesQryExe categories = mock(ListCategoriesQryExe.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final ListTagsQryExe tags = mock(ListTagsQryExe.class);
    private final VisitRequestFilter visitFilter = mock(VisitRequestFilter.class);
    private final PostController controller = new PostController(listPosts, getPost, createPost, publishPost,
            mock(MarkdownRenderer.class), mock(ListCommentsQryExe.class), tags, categories,
            mock(PostViewCounter.class), posts, mock(SeriesRepository.class), mock(GetSeriesPostsQryExe.class),
            mock(GetPostRatingQryExe.class), visitFilter, mock(ApplicationEventPublisher.class));

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listUsesCategoryAndRetainsPaginationState() {
        when(listPosts.executeByCategory("spring", 2, 3)).thenReturn(List.of());
        when(listPosts.countByCategory("spring")).thenReturn(7L);
        when(tags.findAllWithCount()).thenReturn(List.of());
        when(categories.execute()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.list(model, 2, 3, " ", "spring")).isEqualTo("public/posts/list");
        assertThat(model).containsEntry("totalPages", 3L).containsEntry("hasPrev", true).containsEntry("hasNext", true);
    }

    @Test
    void listTreatsBlankFiltersAsTheUnfilteredQuery() {
        when(listPosts.execute(1, 10)).thenReturn(List.of());
        when(listPosts.countPublished()).thenReturn(0L);
        when(tags.findAllWithCount()).thenReturn(List.of());
        when(categories.execute()).thenReturn(List.of());

        assertThat(controller.list(new ExtendedModelMap(), 1, 10, null, " ")).isEqualTo("public/posts/list");
        verify(listPosts).execute(1, 10);
    }

    @Test
    void newFormSuppliesCommandAndCategories() {
        when(categories.execute()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();

        assertThat(controller.newForm(model)).isEqualTo("admin/posts/edit");
        assertThat(model.get("cmd")).isInstanceOf(CreatePostCmd.class);
        verify(categories).execute();
    }

    @Test
    void createUsesCurrentUserAndFallsBackToNumericIdWhenPostIsGone() {
        authenticate(user(7L, "author"));
        CreatePostCmd command = new CreatePostCmd();
        when(createPost.execute(command)).thenReturn(9L);
        when(posts.findById(9L)).thenReturn(Optional.empty());

        assertThat(controller.create(command)).isEqualTo("redirect:/posts/9");
        assertThat(command.getAuthorUsername()).isEqualTo("author");
    }

    @Test
    void createKeepsAnonymousCommandAndUsesPersistedSlug() {
        CreatePostCmd command = new CreatePostCmd();
        when(createPost.execute(command)).thenReturn(9L);
        when(posts.findById(9L)).thenReturn(Optional.of(post(9L, "saved", PostStatus.DRAFT, 7L)));

        assertThat(controller.create(command)).isEqualTo("redirect:/posts/saved");
        assertThat(command.getAuthorUsername()).isNull();
    }

    @Test
    void publishAllowsOwnerAndManagerButRejectsOtherUsers() {
        PostDTO draft = dto(8L, "draft", "DRAFT", 7L);
        when(getPost.executeBySlug("draft")).thenReturn(draft);

        authenticate(user(7L, "owner"));
        assertThat(controller.publish("draft")).isEqualTo("redirect:/posts/draft");
        verify(publishPost).execute(8L);

        authenticate(user(9L, "manager", "blog:post:manage"));
        assertThat(controller.publish("draft")).isEqualTo("redirect:/posts/draft");

        authenticate(user(3L, "other"));
        assertThatThrownBy(() -> controller.publish("draft")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void draftVisibilityAllowsOwnerAndManagerButHidesItFromOtherUsers() {
        PostDTO draft = dto(10L, "private-post", "DRAFT", 7L);
        when(getPost.executeBySlug("private-post")).thenReturn(draft);
        when(posts.findById(10L)).thenReturn(Optional.of(post(10L, "private-post", PostStatus.DRAFT, 7L)));
        when(posts.findPrevPublished(10L)).thenReturn(Optional.empty());
        when(posts.findNextPublished(10L)).thenReturn(Optional.empty());
        when(visitFilter.shouldRecord(any())).thenReturn(false);

        authenticate(user(3L, "other"));
        assertThatThrownBy(() -> controller.detail("private-post", new ExtendedModelMap(), request()))
                .isInstanceOf(java.util.NoSuchElementException.class);

        authenticate(user(7L, "owner"));
        ExtendedModelMap ownerModel = new ExtendedModelMap();
        assertThat(controller.detail("private-post", ownerModel, request())).isEqualTo("public/posts/detail");
        assertThat(ownerModel).containsEntry("canPublish", true);

        authenticate(user(3L, "manager", "blog:post:manage"));
        assertThat(controller.detail("private-post", new ExtendedModelMap(), request())).isEqualTo("public/posts/detail");
    }

    @Test
    void numericDetailReportsMissingLegacyPosts() {
        when(posts.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.detail("404", new ExtendedModelMap(), request()))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("404");
    }

    @Test
    void helperPoliciesHandleCookiesSecurityAndTrustedProxyAddresses() {
        HttpServletRequest noCookie = request();
        assertThat(WebUtils.readCookie(noCookie, PostRatingController.VISITOR_COOKIE)).isNull();
        HttpServletRequest unrelatedCookie = request(new Cookie("other", "x"));
        assertThat(WebUtils.readCookie(unrelatedCookie, PostRatingController.VISITOR_COOKIE)).isNull();
        HttpServletRequest ratingCookie = request(new Cookie(PostRatingController.VISITOR_COOKIE, "visitor"));
        assertThat(WebUtils.readCookie(ratingCookie, PostRatingController.VISITOR_COOKIE)).isEqualTo("visitor");

        SecurityContextHolder.clearContext();
        assertThat(SecurityUtils.currentUser()).isNull();
        org.springframework.security.core.Authentication unauthenticated = mock(org.springframework.security.core.Authentication.class);
        when(unauthenticated.isAuthenticated()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        assertThat(SecurityUtils.currentUser()).isNull();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("text", "n/a", List.of()));
        assertThat(SecurityUtils.currentUser()).isNull();
        SiteUserDetails owner = user(7L, "owner");
        authenticate(owner);
        assertThat(SecurityUtils.currentUser()).isSameAs(owner);
        assertThat(SecurityUtils.hasAuthority(owner, "missing")).isEqualTo(false);
        assertThat(SecurityUtils.hasAuthority(user(7L, "manager", "blog:post:manage"), "blog:post:manage")).isEqualTo(true);
        assertThat(SecurityUtils.isOwner(null, 7L)).isEqualTo(false);
        assertThat(SecurityUtils.isOwner(owner, null)).isEqualTo(false);
        assertThat(SecurityUtils.isOwner(owner, 7L)).isEqualTo(true);
        assertThat(SecurityUtils.isOwner(owner, 8L)).isEqualTo(false);
        assertThat(SecurityUtils.isOwner(
                new org.springframework.security.core.userdetails.User("plain", "hash", List.of()), 7L)).isEqualTo(false);
        assertThat(SecurityUtils.extractUserId(owner)).isEqualTo(7L);

        HttpServletRequest forwarded = request();
        when(forwarded.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, , 8.8.8.8");
        assertThat(WebUtils.getClientIp(forwarded)).isEqualTo("8.8.8.8");
        HttpServletRequest privateOnly = request();
        when(privateOnly.getHeader("X-Forwarded-For")).thenReturn("127.0.0.1, 192.168.1.2");
        when(privateOnly.getRemoteAddr()).thenReturn("203.0.113.10");
        assertThat(WebUtils.getClientIp(privateOnly)).isEqualTo("203.0.113.10");
        HttpServletRequest blankForwarded = request();
        when(blankForwarded.getHeader("X-Forwarded-For")).thenReturn(" ");
        when(blankForwarded.getRemoteAddr()).thenReturn("203.0.113.11");
        assertThat(WebUtils.getClientIp(blankForwarded)).isEqualTo("203.0.113.11");
        assertThat(WebUtils.truncate(null, 2)).isNull();
        assertThat(WebUtils.truncate("ab", 2)).isEqualTo("ab");
        assertThat(WebUtils.truncate("abc", 2)).isEqualTo("ab");
    }

    private static PostDTO dto(Long id, String slug, String status, Long authorId) {
        PostDTO result = new PostDTO();
        result.setId(id);
        result.setSlug(slug);
        result.setStatus(status);
        result.setAuthorId(authorId);
        result.setContent("content");
        return result;
    }

    private static Post post(Long id, String slug, PostStatus status, Long authorId) {
        return Post.reconstruct(id, slug, "title", "content", status, LocalDateTime.now(), LocalDateTime.now(),
                LocalDateTime.now(), null, authorId, false);
    }

    private static SiteUserDetails user(Long id, String username, String... authorities) {
        return new SiteUserDetails(id, username, "hash", List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new).toList());
    }

    private static void authenticate(Object principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, "n/a", principal instanceof SiteUserDetails details
                        ? details.getAuthorities() : List.of()));
    }

    private static HttpServletRequest request(Cookie... cookies) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(cookies.length == 0 ? null : cookies);
        return request;
    }

    }
