package manfred.bytedepth.app.post.command;

import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SetPostTagsCmdExeTest {

    @Mock
    private TagRepository tagRepository;

    private SetPostTagsCmdExe setPostTagsCmdExe;

    @BeforeEach
    void setUp() {
        setPostTagsCmdExe = new SetPostTagsCmdExe(tagRepository);
    }

    @Test
    void execute_withPureSlugFormat_usesSlugAsName() {
        Tag newTag = Tag.reconstruct(1L, "java", "java");
        when(tagRepository.findBySlug("java")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

        setPostTagsCmdExe.execute(100L, List.of("java"));

        // Tag.create("java", "java") — name equals slug
        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(captor.capture());
        assertEquals("java", captor.getValue().getName());
        assertEquals("java", captor.getValue().getSlug());
        verify(tagRepository).savePostTags(eq(100L), eq(List.of(1L)));
    }

    @Test
    void execute_withSlugDisplayNameFormat_usesDisplayName() {
        Tag newTag = Tag.reconstruct(2L, "Java编程", "java");
        when(tagRepository.findBySlug("java")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

        setPostTagsCmdExe.execute(100L, List.of("java:Java编程"));

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(captor.capture());
        assertEquals("Java编程", captor.getValue().getName());
        assertEquals("java", captor.getValue().getSlug());
    }

    @Test
    void execute_withExistingTagSameName_returnsExistingWithoutSave() {
        Tag existing = Tag.reconstruct(3L, "java", "java");
        when(tagRepository.findBySlug("java")).thenReturn(Optional.of(existing));

        setPostTagsCmdExe.execute(100L, List.of("java"));

        // Should NOT call save (no update needed) — only savePostTags
        verify(tagRepository, never()).save(any(Tag.class));
        verify(tagRepository).savePostTags(eq(100L), eq(List.of(3L)));
    }

    @Test
    void execute_withExistingTagDifferentName_updatesTag() {
        Tag existing = Tag.reconstruct(4L, "旧名称", "java");
        Tag updated = Tag.reconstruct(4L, "新名称", "java");
        when(tagRepository.findBySlug("java")).thenReturn(Optional.of(existing));
        when(tagRepository.save(any(Tag.class))).thenReturn(updated);

        setPostTagsCmdExe.execute(100L, List.of("java:新名称"));

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(captor.capture());
        Tag saved = captor.getValue();
        assertEquals(4L, saved.getId());
        assertEquals("新名称", saved.getName());
        assertEquals("java", saved.getSlug());
        verify(tagRepository).savePostTags(eq(100L), eq(List.of(4L)));
    }

    @Test
    void execute_withNullAndBlankSpecs_filtersThemOut() {
        Tag newTag = Tag.reconstruct(5L, "spring", "spring");
        when(tagRepository.findBySlug("spring")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

        setPostTagsCmdExe.execute(100L, Arrays.asList(null, "", "  ", "spring"));

        verify(tagRepository).savePostTags(eq(100L), eq(List.of(5L)));
    }

    @Test
    void execute_withMultipleTags_collectsAllIds() {
        Tag tag1 = Tag.reconstruct(1L, "java", "java");
        Tag tag2 = Tag.reconstruct(2L, "Spring框架", "spring");
        when(tagRepository.findBySlug("java")).thenReturn(Optional.empty());
        when(tagRepository.findBySlug("spring")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(tag1, tag2);

        setPostTagsCmdExe.execute(100L, List.of("java", "spring:Spring框架"));

        verify(tagRepository).savePostTags(eq(100L), eq(List.of(1L, 2L)));
    }

    @Test
    void execute_withEmptyTagList_savesEmptyList() {
        setPostTagsCmdExe.execute(100L, List.of());

        verify(tagRepository, never()).findBySlug(any());
        verify(tagRepository, never()).save(any(Tag.class));
        verify(tagRepository).savePostTags(eq(100L), eq(List.of()));
    }

    @Test
    void execute_withSlugHavingWhitespace_trimsAndLowercases() {
        Tag newTag = Tag.reconstruct(6L, "docker", "docker");
        when(tagRepository.findBySlug("docker")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

        setPostTagsCmdExe.execute(100L, List.of("  Docker  "));

        verify(tagRepository).findBySlug("docker");
        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(captor.capture());
        assertEquals("docker", captor.getValue().getSlug());
    }

    @Test
    void execute_withColonButEmptyDisplayName_usesSlugAsName() {
        // "java:" → parts = ["java", ""] → name = "".trim() = "" → but parts.length > 1, name = ""
        // Actually: parts[1].trim() = "" → name = ""
        // This is an edge case: slug:name format with empty name
        Tag newTag = Tag.reconstruct(7L, "", "java");
        when(tagRepository.findBySlug("java")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenReturn(newTag);

        setPostTagsCmdExe.execute(100L, List.of("java: "));

        ArgumentCaptor<Tag> captor = ArgumentCaptor.forClass(Tag.class);
        verify(tagRepository).save(captor.capture());
        assertEquals("java", captor.getValue().getSlug());
        // name = "" (trimmed from " ")
        assertEquals("", captor.getValue().getName());
    }
}
