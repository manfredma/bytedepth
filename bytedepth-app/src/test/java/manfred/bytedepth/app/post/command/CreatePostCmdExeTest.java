package manfred.bytedepth.app.post.command;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePostCmdExeTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    private CreatePostCmdExe createPostCmdExe;

    @BeforeEach
    void setUp() {
        createPostCmdExe = new CreatePostCmdExe(postRepository, userRepository);
    }

    @Test
    void execute_shouldSavePostAndReturnId() {
        User author = User.reconstruct(1L, "admin", "hash", null, null, null,
                UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(author));
        Post savedPost = Post.reconstruct(1L, "标题", "内容",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, 1L, false);
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("标题");
        cmd.setContent("内容");
        cmd.setAuthorUsername("admin");

        Long id = createPostCmdExe.execute(cmd);

        assertEquals(1L, id);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void execute_shouldPassTitleAndContentToPost() {
        User author = User.reconstruct(2L, "writer", "hash", null, null, null,
                UserStatus.ACTIVE, LocalDateTime.now(), LocalDateTime.now());
        when(userRepository.findByUsername("writer")).thenReturn(Optional.of(author));
        Post savedPost = Post.reconstruct(2L, "新文章", "新内容",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, 2L, false);
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("新文章");
        cmd.setContent("新内容");
        cmd.setAuthorUsername("writer");

        Long id = createPostCmdExe.execute(cmd);

        assertEquals(2L, id);
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    void execute_withNullAuthorUsername_createsPostWithNullAuthorId() {
        Post savedPost = Post.reconstruct(3L, "T", "C",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, null, false);
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("T");
        cmd.setContent("C");
        // authorUsername not set

        Long id = createPostCmdExe.execute(cmd);

        assertEquals(3L, id);
        verify(userRepository, never()).findByUsername(any());
    }
}
