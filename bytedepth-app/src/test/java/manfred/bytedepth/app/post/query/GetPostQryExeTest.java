package manfred.bytedepth.app.post.query;

import manfred.bytedepth.domain.post.Post;
import manfred.bytedepth.domain.post.PostRepository;
import manfred.bytedepth.domain.post.PostStatus;
import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetPostQryExeTest {

    @Mock private PostRepository postRepository;
    @Mock private TagRepository tagRepository;
    private GetPostQryExe exe;

    @BeforeEach
    void setUp() {
        exe = new GetPostQryExe(postRepository, tagRepository);
    }

    @Test
    void executeById_found_returnsDtoWithTags() {
        LocalDateTime now = LocalDateTime.now();
        Post post = Post.reconstruct(1L, "java-guide", "Java 指南", "内容", PostStatus.PUBLISHED,
                now, now, now, 7L, 9L, false);
        when(postRepository.findById(1L)).thenReturn(Optional.of(post));
        when(tagRepository.findByPostId(1L)).thenReturn(List.of(Tag.reconstruct(2L, "Java", "java")));

        PostDTO dto = exe.execute(1L);

        assertEquals(1L, dto.getId());
        assertEquals("java-guide", dto.getSlug());
        assertEquals("Java 指南", dto.getTitle());
        assertEquals("内容", dto.getContent());
        assertEquals("PUBLISHED", dto.getStatus());
        assertEquals(7L, dto.getCategoryId());
        assertEquals(9L, dto.getAuthorId());
        assertEquals(List.of("java"), dto.getTagSlugs());
        verify(tagRepository).findByPostId(1L);
    }

    @Test
    void executeById_missing_throwsNoSuchElement() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> exe.execute(99L));
    }

    @Test
    void executeBySlug_found_returnsDtoWithoutTags() {
        LocalDateTime now = LocalDateTime.now();
        Post post = Post.reconstruct(5L, "spring", "Spring", "body", PostStatus.DRAFT,
                now, null, now, null, 2L, false);
        when(postRepository.findBySlug("spring")).thenReturn(Optional.of(post));
        when(tagRepository.findByPostId(5L)).thenReturn(List.of());

        PostDTO dto = exe.executeBySlug("spring");

        assertEquals(5L, dto.getId());
        assertEquals("spring", dto.getSlug());
        assertEquals("Spring", dto.getTitle());
        assertEquals("DRAFT", dto.getStatus());
        assertNull(dto.getCategoryId());
        assertNull(dto.getPublishedAt());
        assertTrue(dto.getTagSlugs().isEmpty());
    }

    @Test
    void executeBySlug_missing_throwsNoSuchElement() {
        when(postRepository.findBySlug("nope")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> exe.executeBySlug("nope"));
    }
}
