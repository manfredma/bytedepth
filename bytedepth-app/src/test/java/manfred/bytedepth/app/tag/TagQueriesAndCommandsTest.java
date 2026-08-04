package manfred.bytedepth.app.tag;

import manfred.bytedepth.domain.tag.Tag;
import manfred.bytedepth.domain.tag.TagRepository;
import manfred.bytedepth.domain.tag.TagWithCount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TagQueriesAndCommandsTest {

    private final TagRepository repository = mock(TagRepository.class);

    @Test
    void create_reusesExistingTagAndCreatesMissingTag() {
        CreateTagCmdExe command = new CreateTagCmdExe(repository);
        when(repository.findBySlug("java")).thenReturn(Optional.of(Tag.reconstruct(1L, "Java", "java")));
        assertEquals(1L, command.execute("ignored", "java").getId());
        verify(repository, never()).save(any());

        when(repository.findBySlug("spring")).thenReturn(Optional.empty());
        when(repository.save(any())).thenReturn(Tag.reconstruct(2L, "Spring", "spring"));
        TagDTO created = command.execute("Spring", "spring");
        assertEquals("Spring", created.getName());
        assertEquals("spring", created.getSlug());
    }

    @Test
    void list_mapsEveryRepositoryProjection() {
        ListTagsQryExe query = new ListTagsQryExe(repository);
        when(repository.findAll()).thenReturn(List.of(Tag.reconstruct(1L, "Java", "java")));
        when(repository.findByPostId(4L)).thenReturn(List.of(Tag.reconstruct(2L, "Spring", "spring")));
        when(repository.findAllWithCount()).thenReturn(List.of(new TagWithCount(3L, "DDD", "ddd", 7)));

        assertEquals("java", query.execute().getFirst().getSlug());
        assertEquals("Spring", query.findByPostId(4L).getFirst().getName());
        TagDTO counted = query.findAllWithCount().getFirst();
        assertEquals(3L, counted.getId());
        assertEquals(7, counted.getCount());
    }
}
