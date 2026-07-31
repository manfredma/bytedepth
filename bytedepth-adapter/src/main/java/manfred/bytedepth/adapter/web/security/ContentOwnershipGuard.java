package manfred.bytedepth.adapter.web.security;

import lombok.RequiredArgsConstructor;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.series.Series;
import manfred.bytedepth.domain.series.SeriesRepository;
import manfred.bytedepth.infrastructure.user.SiteUserDetails;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/** Enforces server-side ownership checks for personal content management routes. */
@Component
@RequiredArgsConstructor
public class ContentOwnershipGuard {

    private static final String POST_MANAGE = "blog:post:manage";
    private static final String SERIES_MANAGE = "blog:series:manage";

    private final PostRepository postRepository;
    private final SeriesRepository seriesRepository;

    public Long currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof SiteUserDetails user) {
            return user.getId();
        }
        throw new AccessDeniedException("需要有效登录用户");
    }

    public boolean canManagePosts(Authentication authentication) {
        return hasAuthority(authentication, POST_MANAGE);
    }

    public boolean canManageSeries(Authentication authentication) {
        return hasAuthority(authentication, SERIES_MANAGE);
    }

    public Post requirePostOwner(Authentication authentication, Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("博文不存在：" + postId));
        if (!canManagePosts(authentication) && !post.getAuthorId().equals(currentUserId(authentication))) {
            throw new AccessDeniedException("无权操作其他作者的文章");
        }
        return post;
    }

    public Series requireSeriesOwner(Authentication authentication, Long seriesId) {
        Series series = seriesRepository.findById(seriesId)
                .orElseThrow(() -> new IllegalArgumentException("专栏不存在：" + seriesId));
        if (!canManageSeries(authentication) && !series.getAuthorId().equals(currentUserId(authentication))) {
            throw new AccessDeniedException("无权操作其他作者的专栏");
        }
        return series;
    }

    private boolean hasAuthority(Authentication authentication, String authority) {
        return authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }
}
