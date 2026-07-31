package manfred.bytedepth.adapter.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.infrastructure.user.SiteUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

class ContentOwnershipGuardTest {

    private PostRepository postRepository;
    private SeriesRepository seriesRepository;
    private ContentOwnershipGuard guard;

    @BeforeEach
    void setUp() {
        postRepository = org.mockito.Mockito.mock(PostRepository.class);
        seriesRepository = org.mockito.Mockito.mock(SeriesRepository.class);
        guard = new ContentOwnershipGuard(postRepository, seriesRepository);
    }

    @Test
    void returnsTheAuthenticatedUserIdAndRecognizesManagementAuthorities() {
        var postManager = authentication(7L, "blog:post:manage");
        var seriesManager = authentication(7L, "blog:series:manage");

        assertThat(guard.currentUserId(postManager)).isEqualTo(7L);
        assertThat(guard.canManagePosts(postManager)).isTrue();
        assertThat(guard.canManageSeries(postManager)).isFalse();
        assertThat(guard.canManageSeries(seriesManager)).isTrue();
        assertThat(guard.canManagePosts(null)).isFalse();
    }

    @Test
    void rejectsMissingOrUnsupportedPrincipals() {
        assertThatThrownBy(() -> guard.currentUserId(null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.currentUserId(new TestingAuthenticationToken("user", "password")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void permitsPostOwnersAndPostManagersOnly() {
        Post post = Post.create("title", "content", 11L);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));

        assertThat(guard.requirePostOwner(authentication(11L), 1L)).isSameAs(post);
        assertThat(guard.requirePostOwner(authentication(22L, "blog:post:manage"), 1L)).isSameAs(post);
        assertThatThrownBy(() -> guard.requirePostOwner(authentication(22L), 1L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.requirePostOwner(authentication(11L), 2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void permitsSeriesOwnersAndSeriesManagersOnly() {
        Series series = Series.create("series", "series", null, 11L);
        when(seriesRepository.findById(1L)).thenReturn(Optional.of(series));

        assertThat(guard.requireSeriesOwner(authentication(11L), 1L)).isSameAs(series);
        assertThat(guard.requireSeriesOwner(authentication(22L, "blog:series:manage"), 1L)).isSameAs(series);
        assertThatThrownBy(() -> guard.requireSeriesOwner(authentication(22L), 1L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> guard.requireSeriesOwner(authentication(11L), 2L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private TestingAuthenticationToken authentication(Long id, String... authorities) {
        var user = new SiteUserDetails(id, "user-" + id, "password", List.of(authorities).stream()
                .map(SimpleGrantedAuthority::new).toList());
        return new TestingAuthenticationToken(user, "password", user.getAuthorities());
    }
}
