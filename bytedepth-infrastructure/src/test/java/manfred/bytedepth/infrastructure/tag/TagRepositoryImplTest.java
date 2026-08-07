package manfred.bytedepth.infrastructure.tag;

import manfred.bytedepth.domain.tag.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagRepositoryImplTest {

    private final TagMapper tagMapper = Mockito.mock(TagMapper.class);
    private final TagRepositoryImpl repository = new TagRepositoryImpl(tagMapper);

    @Test
    void save_insertsNewTagAndUpdatesExistingTag() {
        when(tagMapper.insert(any(TagDO.class))).thenAnswer(invocation -> {
            invocation.<TagDO>getArgument(0).setId(1L);
            return 1;
        });

        Tag created = repository.save(Tag.create("Java", "java"));
        Tag updated = repository.save(Tag.reconstruct(1L, "Spring", "spring"));

        assertEquals(1L, created.getId());
        assertEquals("Java", created.getName());
        assertEquals("spring", updated.getSlug());
        verify(tagMapper).updateById(any(TagDO.class));
    }

    @Test
    void findBySlugAndId_mapExistingRowsAndHandleMissingRows() {
        TagDO row = tagRow();
        when(tagMapper.selectOne(any())).thenReturn(row).thenReturn((TagDO) null);
        when(tagMapper.selectById(1L)).thenReturn(row);
        when(tagMapper.selectById(2L)).thenReturn(null);

        assertEquals("Java", repository.findBySlug("java").orElseThrow().getName());
        assertFalse(repository.findBySlug("missing").isPresent());
        assertEquals("java", repository.findById(1L).orElseThrow().getSlug());
        assertTrue(repository.findById(2L).isEmpty());
    }

    @Test
    void findAllAndFindByPostId_mapTagRows() {
        TagDO row = tagRow();
        when(tagMapper.selectList(isNull())).thenReturn(List.of(row));
        when(tagMapper.findByPostId(9L)).thenReturn(List.of(row));

        assertEquals("Java", repository.findAll().get(0).getName());
        assertEquals(1L, repository.findByPostId(9L).get(0).getId());
    }

    @Test
    void savePostTags_replacesAssociationsAndHandlesEmptyInput() {
        repository.savePostTags(9L, List.of(1L, 2L));
        repository.savePostTags(10L, List.of());
        repository.savePostTags(11L, null);

        verify(tagMapper).deletePostTags(9L);
        verify(tagMapper).insertPostTags(9L, List.of(1L, 2L));
        verify(tagMapper).deletePostTags(10L);
        verify(tagMapper).deletePostTags(11L);
    }

    @Test
    void findAllWithCount_mapsRows() {
        TagWithCountDO row = new TagWithCountDO();
        row.setId(1L);
        row.setName("Java");
        row.setSlug("java");
        row.setPostCount(3L);
        when(tagMapper.findAllWithCount()).thenReturn(List.of(row));

        var tag = repository.findAllWithCount().get(0);

        assertEquals("Java", tag.getName());
        assertEquals(3L, tag.getCount());
    }

    @Test
    void deleteWithPostAssociations_clearsAssociationsBeforeDeletingTag() {
        repository.deleteWithPostAssociations(3L);

        var order = Mockito.inOrder(tagMapper);
        order.verify(tagMapper).deletePostTagAssociations(3L);
        order.verify(tagMapper).deleteById(3L);
    }

    private TagDO tagRow() {
        TagDO row = new TagDO();
        row.setId(1L);
        row.setName("Java");
        row.setSlug("java");
        return row;
    }
}
