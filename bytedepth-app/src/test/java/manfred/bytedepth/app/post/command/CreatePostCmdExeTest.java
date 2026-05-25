package manfred.bytedepth.app.post.command;

import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreatePostCmdExeTest {

    @Mock
    private PostRepository postRepository;

    private CreatePostCmdExe createPostCmdExe;

    @BeforeEach
    void setUp() {
        createPostCmdExe = new CreatePostCmdExe(postRepository);
    }

    @Test
    void execute_shouldSavePostAndReturnId() {
        Post savedPost = Post.reconstruct(1L, "标题", "内容",
                PostStatus.DRAFT,
                LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("标题");
        cmd.setContent("内容");

        Long id = createPostCmdExe.execute(cmd);

        assertEquals(1L, id);
        verify(postRepository).save(any(Post.class));
    }

    @Test
    void execute_shouldPassTitleAndContentToPost() {
        Post savedPost = Post.reconstruct(2L, "新文章", "新内容",
                PostStatus.DRAFT,
                LocalDateTime.now(), null, LocalDateTime.now());
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("新文章");
        cmd.setContent("新内容");

        Long id = createPostCmdExe.execute(cmd);

        assertEquals(2L, id);
        verify(postRepository, times(1)).save(any(Post.class));
    }
}
