package manfred.bytedepth.adapter.web.portal;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import manfred.bytedepth.app.comment.SubmitCommentCmdExe;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.app.project.ListProjectsQryExe;
import manfred.bytedepth.app.rating.RatePostCmdExe;
import manfred.bytedepth.app.search.SearchPostsQryExe;
import manfred.bytedepth.app.series.GetSeriesForPortalQryExe;
import manfred.bytedepth.app.series.ListSeriesQryExe;
import manfred.bytedepth.app.series.SeriesCardDTO;
import manfred.bytedepth.app.series.SeriesPortalDTO;
import manfred.bytedepth.adapter.web.util.SearchHighlight;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.search.SearchResult;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SimplePortalControllerCoverageTest {

    @Test
    void staticAndProjectRoutesReturnExpectedViewsAndModel() {
        assertThat(new AboutController().about()).isEqualTo("public/about");
        assertThat(new LoginController().login()).isEqualTo("public/login");

        ListProjectsQryExe projects = mock(ListProjectsQryExe.class);
        when(projects.execute()).thenReturn(List.of());
        ExtendedModelMap model = new ExtendedModelMap();
        assertThat(new ProjectController(projects).list(model)).isEqualTo("public/projects/list");
        assertThat(model).containsKey("projects");
    }

    @Test
    void commentSubmissionUsesResolvedPostAndRejectsUnknownSlug() {
        SubmitCommentCmdExe submit = mock(SubmitCommentCmdExe.class);
        PostRepository posts = mock(PostRepository.class);
        Post post = Post.reconstruct(7L, "comment", "title", "content", null, null, null, null, null, null, false);
        when(posts.findBySlug("comment")).thenReturn(Optional.of(post));
        var user = new org.springframework.security.core.userdetails.User("reader", "n/a", List.of());
        CommentController controller = new CommentController(submit, posts);

        assertThat(controller.submit("comment", "hello", user)).isEqualTo("redirect:/posts/comment#comments");
        verify(submit).execute(7L, "reader", "hello");
        when(posts.findBySlug("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> controller.submit("missing", "hello", user))
                .isInstanceOf(NoSuchElementException.class).hasMessageContaining("missing");
    }

    @Test
    void ratingValidatesScoresAndUsesExistingOrNewVisitorCookie() {
        GetPostQryExe getPost = mock(GetPostQryExe.class);
        RatePostCmdExe rate = mock(RatePostCmdExe.class);
        PostDTO post = new PostDTO();
        post.setId(3L);
        when(getPost.executeBySlug("rated")).thenReturn(post);
        PostRatingController controller = new PostRatingController(getPost, rate);

        assertThatThrownBy(() -> controller.rate("rated", 0, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.rate("rated", 6, new MockHttpServletRequest(), new MockHttpServletResponse()))
                .isInstanceOf(ResponseStatusException.class);

        MockHttpServletRequest knownVisitor = new MockHttpServletRequest();
        knownVisitor.setSecure(true);
        knownVisitor.setCookies(new Cookie(PostRatingController.VISITOR_COOKIE, "known"));
        assertThat(controller.rate("rated", 5, knownVisitor, new MockHttpServletResponse()))
                .isEqualTo("redirect:/posts/rated#post-rating-end");
        verify(rate).execute(3L, "known", 5);

        MockHttpServletResponse newVisitorResponse = new MockHttpServletResponse();
        MockHttpServletRequest unknownVisitor = new MockHttpServletRequest();
        unknownVisitor.setCookies(new Cookie("other", "ignored"));
        assertThat(controller.rate("rated", 3, unknownVisitor, new MockHttpServletResponse()))
                .isEqualTo("redirect:/posts/rated#post-rating-end");
        assertThat(controller.rate("rated", 1, new MockHttpServletRequest(), newVisitorResponse))
                .isEqualTo("redirect:/posts/rated#post-rating-end");
        assertThat(newVisitorResponse.getHeader("Set-Cookie")).contains(PostRatingController.VISITOR_COOKIE).contains("HttpOnly");
    }

    @Test
    void columnAndSearchRoutesPopulateModelsAndKeepNotFoundBehavior() {
        ListSeriesQryExe list = mock(ListSeriesQryExe.class);
        GetSeriesForPortalQryExe detail = mock(GetSeriesForPortalQryExe.class);
        ListSeriesQryExe.PageResult page = new ListSeriesQryExe.PageResult(List.<SeriesCardDTO>of(), 0, 2, 1);
        when(list.execute(2)).thenReturn(page);
        SeriesPortalDTO series = new SeriesPortalDTO();
        when(detail.execute("java", 3)).thenReturn(series);
        ColumnController columns = new ColumnController(list, detail);
        ExtendedModelMap listModel = new ExtendedModelMap();
        assertThat(columns.list(listModel, 2)).isEqualTo("public/columns/list");
        assertThat(listModel).containsEntry("currentPage", 2).containsKey("seriesList");
        ExtendedModelMap detailModel = new ExtendedModelMap();
        assertThat(columns.detail("java", 3, detailModel)).isEqualTo("public/columns/detail");
        assertThat(detailModel).containsEntry("series", series);
        when(detail.execute("missing", 1)).thenThrow(new NoSuchElementException("missing"));
        assertThatThrownBy(() -> columns.detail("missing", 1, new ExtendedModelMap())).isInstanceOf(NoSuchElementException.class);

        SearchPostsQryExe search = mock(SearchPostsQryExe.class);
        when(search.execute("query", 2)).thenReturn(new SearchResult(List.of(), 11, 2, 10));
        ExtendedModelMap searchModel = new ExtendedModelMap();
        SearchHighlight searchHighlight = new SearchHighlight();
        assertThat(new SearchController(search, searchHighlight).search("query", 2, searchModel))
                .isEqualTo("public/search");
        assertThat(searchModel).containsEntry("q", "query").containsEntry("hasPrev", true)
                .containsEntry("hasNext", false).containsEntry("searchHighlight", searchHighlight);
    }

    @Test
    void sitemapIncludesEscapedContentAndAllDateFallbacks() {
        PostRepository posts = mock(PostRepository.class);
        SeriesRepository series = mock(SeriesRepository.class);
        Post updated = mock(Post.class);
        Post published = mock(Post.class);
        Post fallback = mock(Post.class);
        Post blank = mock(Post.class);
        Post nullSlug = mock(Post.class);
        when(updated.getSlug()).thenReturn("a&b");
        when(updated.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 4, 1, 0));
        when(updated.getSeriesId()).thenReturn(1L);
        when(published.getSlug()).thenReturn("published");
        when(published.getPublishedAt()).thenReturn(LocalDateTime.of(2026, 8, 3, 1, 0));
        when(published.getSeriesId()).thenReturn(1L);
        when(fallback.getSlug()).thenReturn("fallback");
        when(fallback.getSeriesId()).thenReturn(2L);
        when(blank.getSlug()).thenReturn(" ");
        when(blank.getSeriesId()).thenReturn(null);
        when(nullSlug.getSlug()).thenReturn(null);
        Series valid = mock(Series.class);
        Series empty = mock(Series.class);
        Series blankSeries = mock(Series.class);
        when(valid.getSlug()).thenReturn("series<one>");
        when(valid.getId()).thenReturn(1L);
        when(empty.getSlug()).thenReturn(null);
        when(blankSeries.getSlug()).thenReturn(" ");
        when(posts.findAllPublished()).thenReturn(List.of(updated, published, fallback, blank, nullSlug));
        when(series.findAll()).thenReturn(List.of(valid, empty, blankSeries));
        SitemapController controller = new SitemapController(posts, series);
        ReflectionTestUtils.setField(controller, "siteUrl", "https://example.test?a=1&b=2");

        String xml = controller.sitemap();
        assertThat(xml).contains("https://example.test?a=1&amp;b=2/posts/a&amp;b")
                .contains("2026-08-04").contains("2026-08-03")
                .contains("series&lt;one&gt;").doesNotContain("posts/ ")
                .doesNotContain("<loc>https://example.test?a=1&amp;b=2/about</loc>\n    <lastmod>");

        when(posts.findAllPublished()).thenReturn(List.of(published, updated));
        assertThat(controller.sitemap()).contains("<loc>https://example.test?a=1&amp;b=2/columns/series&lt;one&gt;</loc>\n    <lastmod>2026-08-04</lastmod>");
    }

    @Test
    void feedListsRecentPostsWithSafeSummariesAndOmitsUnavailableDates() {
        PostRepository posts = mock(PostRepository.class);
        Post published = mock(Post.class);
        Post updated = mock(Post.class);
        Post undated = mock(Post.class);
        when(published.getSlug()).thenReturn("published");
        when(published.getTitle()).thenReturn("A & B");
        when(published.getContent()).thenReturn("short\nsummary");
        when(published.getPublishedAt()).thenReturn(LocalDateTime.of(2026, 8, 3, 1, 0));
        when(updated.getSlug()).thenReturn("updated");
        when(updated.getTitle()).thenReturn(null);
        when(updated.getContent()).thenReturn("x".repeat(301));
        when(updated.getUpdatedAt()).thenReturn(LocalDateTime.of(2026, 8, 4, 1, 0));
        when(undated.getSlug()).thenReturn("undated");
        when(undated.getTitle()).thenReturn("undated");
        when(posts.findAllPublished()).thenReturn(List.of(published, updated, undated));
        FeedController controller = new FeedController(posts);
        ReflectionTestUtils.setField(controller, "siteUrl", "https://example.test?a=1&b=2");

        String xml = controller.feed();

        assertThat(xml).contains("<title>A &amp; B</title>", "short summary", "Tue, 4 Aug 2026 01:00:00 +0800")
                .contains("https://example.test?a=1&amp;b=2/feed.xml")
                .doesNotContain("<title>null</title>")
                .doesNotContain("<link>https://example.test?a=1&amp;b=2/posts/undated</link>\n<guid isPermaLink=\"true\">https://example.test?a=1&amp;b=2/posts/undated</guid>\n<description></description>\n<pubDate>");

        when(posts.findAllPublished()).thenReturn(List.of());
        assertThat(controller.feed()).doesNotContain("<lastBuildDate>");
    }
}
