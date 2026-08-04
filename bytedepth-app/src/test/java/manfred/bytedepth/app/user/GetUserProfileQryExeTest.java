package manfred.bytedepth.app.user;

import manfred.bytedepth.app.post.query.PostDTO;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetUserProfileQryExeTest {

    @Mock private UserRepository userRepository;
    @Mock private PostRepository postRepository;
    private GetUserProfileQryExe exe;

    @BeforeEach
    void setUp() { exe = new GetUserProfileQryExe(userRepository, postRepository); }

    @Test
    void execute_existingUser_returnsProfile() {
        User user = User.reconstruct(1L, "alice", "hash", null, null, "My bio",
                UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(postRepository.findPublishedByAuthorId(1L, 1, 10)).thenReturn(List.of());
        when(postRepository.countPublishedByAuthorId(1L)).thenReturn(0L);

        UserProfileDTO profile = exe.execute("alice");

        assertEquals("alice", profile.getUsername());
        assertEquals("My bio", profile.getBio());
        assertEquals(0, profile.getPostCount());
        assertTrue(profile.getRecentPosts().isEmpty());
    }

    @Test
    void execute_unknownUser_throwsDomainException() {
        when(userRepository.findByUsername("nobody")).thenReturn(Optional.empty());
        assertThrows(DomainException.class, () -> exe.execute("nobody"));
    }

    @Test
    void execute_userWithPublishedPosts_mapsRecentPostsAndCount() {
        User user = User.reconstruct(2L, "bob", "hash", null, null, "bio",
                UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user));
        Post post = Post.reconstruct(5L, "slug", "标题", "内容", PostStatus.PUBLISHED,
                LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, 2L, false);
        when(postRepository.findPublishedByAuthorId(2L, 1, 10)).thenReturn(List.of(post));
        when(postRepository.countPublishedByAuthorId(2L)).thenReturn(3L);

        UserProfileDTO profile = exe.execute("bob");

        assertEquals(2L, profile.getId());
        assertEquals("bob", profile.getUsername());
        assertEquals("bio", profile.getBio());
        assertEquals(3, profile.getPostCount());
        assertEquals(1, profile.getRecentPosts().size());
        PostDTO recent = profile.getRecentPosts().get(0);
        assertEquals(5L, recent.getId());
        assertEquals("slug", recent.getSlug());
        assertEquals(2L, recent.getAuthorId());
        assertEquals("标题", recent.getTitle());
        assertEquals("PUBLISHED", recent.getStatus());
    }
}
