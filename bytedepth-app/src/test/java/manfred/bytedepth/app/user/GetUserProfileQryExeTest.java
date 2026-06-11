package manfred.bytedepth.app.user;

import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.PostRepository;
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
}
