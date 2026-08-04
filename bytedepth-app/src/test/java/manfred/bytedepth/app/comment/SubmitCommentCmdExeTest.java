package manfred.bytedepth.app.comment;

import manfred.bytedepth.domain.comment.CommentRepository;
import manfred.bytedepth.domain.user.User;
import manfred.bytedepth.domain.user.UserRepository;
import manfred.bytedepth.domain.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import manfred.bytedepth.domain.common.DomainException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SubmitCommentCmdExeTest {

    @Mock private CommentRepository commentRepository;
    @Mock private UserRepository userRepository;
    private SubmitCommentCmdExe exe;

    @BeforeEach
    void setUp() { exe = new SubmitCommentCmdExe(commentRepository, userRepository); }

    @Test
    void execute_savesCommentWithAuthorIdAndNameSnapshot() {
        User user = User.reconstruct(42L, "alice", "hash", null, null, null,
                UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        exe.execute(10L, "alice", "Great post!");

        verify(commentRepository).save(argThat(c ->
            Long.valueOf(42L).equals(c.getAuthorId())
            && "alice".equals(c.getAuthorName())
            && "Great post!".equals(c.getContent())
        ));
    }

    @Test
    void execute_userNotFound_throwsDomainException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        var ex = assertThrows(DomainException.class, () -> exe.execute(10L, "ghost", "hi"));
        assertTrue(ex.getMessage().contains("用户不存在"));
        assertTrue(ex.getMessage().contains("ghost"));
        verifyNoInteractions(commentRepository);
    }
}
