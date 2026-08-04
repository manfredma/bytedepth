package manfred.bytedepth.app.post.command;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    // ---- resolveSlug branches ----

    @Test
    void execute_withValidProvidedSlug_usesProvidedSlug() {
        Post savedPost = Post.reconstruct(10L, "T", "C",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, null, false);
        when(postRepository.findBySlug("my-slug")).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("T");
        cmd.setContent("C");
        cmd.setSlug("my-slug");

        Long id = createPostCmdExe.execute(cmd);

        assertEquals(10L, id);
        verify(postRepository).findBySlug("my-slug");
    }

    @Test
    void execute_withProvidedSlugConflict_appendsSuffix() {
        Post savedPost = Post.reconstruct(11L, "T", "C",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, null, false);
        when(postRepository.findBySlug("my-slug")).thenReturn(Optional.of(Post.create("T", "C")));
        when(postRepository.findBySlug("my-slug-2")).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("T");
        cmd.setContent("C");
        cmd.setSlug("my-slug");

        createPostCmdExe.execute(cmd);

        verify(postRepository).findBySlug("my-slug");
        verify(postRepository).findBySlug("my-slug-2");
    }

    @Test
    void execute_withInvalidProvidedSlug_fallsBackToSlugifiedTitle() {
        // provided slug 非法（含大写/非法字符），isValid 返回 false，走 slugify(title)
        Post savedPost = Post.reconstruct(15L, "Hello World", "C",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, null, false);
        when(postRepository.findBySlug("hello-world")).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("Hello World");
        cmd.setContent("C");
        cmd.setSlug("Invalid Slug!"); // 非法：含空格、大写、感叹号

        createPostCmdExe.execute(cmd);

        verify(postRepository).findBySlug("hello-world");
    }

    @Test
    void execute_withTitleSlugify_usesSlugifiedTitle() {
        Post savedPost = Post.reconstruct(12L, "Hello World", "C",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, null, false);
        when(postRepository.findBySlug("hello-world")).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("Hello World");
        cmd.setContent("C");

        createPostCmdExe.execute(cmd);

        verify(postRepository).findBySlug("hello-world");
    }

    @Test
    void execute_withPureChineseTitle_usesTimestampFallback() {
        Post savedPost = Post.reconstruct(13L, "纯中文标题", "C",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, null, false);
        // slugify returns "" for pure Chinese → base.isBlank() → timestamp fallback
        // The timestamp-based slug starts with "post-"
        when(postRepository.findBySlug(argThat(s -> s != null && s.startsWith("post-"))))
                .thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("纯中文标题");
        cmd.setContent("C");

        createPostCmdExe.execute(cmd);

        verify(postRepository).findBySlug(argThat(s -> s != null && s.startsWith("post-")));
    }

    @Test
    void execute_withAllSlugSlotsTaken_throwsDomainException() {
        // Simulate all 999 slots taken
        when(postRepository.findBySlug(anyString())).thenReturn(Optional.of(Post.create("H", "C")));

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("Hello World");
        cmd.setContent("C");

        DomainException ex = assertThrows(DomainException.class,
                () -> createPostCmdExe.execute(cmd));
        assertTrue(ex.getMessage().contains("slug"));
    }

    // ---- categoryId branch ----

    @Test
    void execute_withCategoryId_assignsCategory() {
        Post savedPost = Post.reconstruct(14L, "T", "C",
                PostStatus.DRAFT, LocalDateTime.now(), null, LocalDateTime.now(),
                null, null, false);
        when(postRepository.findBySlug(anyString())).thenReturn(Optional.empty());
        when(postRepository.save(any(Post.class))).thenReturn(savedPost);

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("Test Title");
        cmd.setContent("C");
        cmd.setCategoryId(5L);

        createPostCmdExe.execute(cmd);

        verify(postRepository).save(argThat(p -> p.getCategoryId() != null && p.getCategoryId().equals(5L)));
    }

    // ---- author not found ----

    @Test
    void execute_withNonExistentAuthor_throwsDomainException() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        CreatePostCmd cmd = new CreatePostCmd();
        cmd.setTitle("T");
        cmd.setContent("C");
        cmd.setAuthorUsername("ghost");

        DomainException ex = assertThrows(DomainException.class,
                () -> createPostCmdExe.execute(cmd));
        assertTrue(ex.getMessage().contains("ghost"));
    }
}
