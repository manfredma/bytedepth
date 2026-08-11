package manfred.bytedepth.infrastructure.post;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorPostRepositoryImplTest {

    private final PostMapper postMapper = Mockito.mock(PostMapper.class);
    private final AuthorPostRepositoryImpl repository = new AuthorPostRepositoryImpl(postMapper);

    @Test
    void findPageByAuthorId_mapsPostsAndPreservesSeriesBinding() {
        PostDO row = row(3L);
        PostDO ungroupedRow = row(null);
        ungroupedRow.setId(10L);
        Page<PostDO> page = new Page<>(2, 5);
        page.setRecords(List.of(row, ungroupedRow));
        when(postMapper.selectPage(any(), any())).thenReturn(page);

        var posts = repository.findPageByAuthorId(7L, 2, 5);

        assertEquals(2, posts.size());
        assertEquals(9L, posts.get(0).getId());
        assertEquals(3L, posts.get(0).getSeriesId());
        assertEquals(2, posts.get(0).getSeriesOrder());
        assertEquals(null, posts.get(1).getSeriesId());
        verify(postMapper).selectPage(any(), any());
    }

    @Test
    void countByAuthorId_delegatesToMapper() {
        when(postMapper.selectCount(any())).thenReturn(4L);

        assertEquals(4L, repository.countByAuthorId(7L));
        verify(postMapper).selectCount(any());
    }

    @Test
    void authorScopedQueriesAlwaysFilterByAuthorAndExcludeDeletedPosts() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PostDO.class);
        Page<PostDO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        when(postMapper.selectPage(any(), any())).thenReturn(page);
        when(postMapper.selectCount(any())).thenReturn(0L);

        repository.findPageByAuthorId(7L, 1, 10);
        repository.countByAuthorId(7L);

        ArgumentCaptor<LambdaQueryWrapper<PostDO>> pageWrapper = wrapperCaptor();
        ArgumentCaptor<LambdaQueryWrapper<PostDO>> countWrapper = wrapperCaptor();
        verify(postMapper).selectPage(any(), pageWrapper.capture());
        verify(postMapper).selectCount(countWrapper.capture());

        assertHasOwnershipAndDeletionFilters(pageWrapper.getValue());
        assertHasOwnershipAndDeletionFilters(countWrapper.getValue());
    }

    @Test
    void filteredAuthorQueries_supportAllOptionalFiltersAndTheirAbsence() {
        Page<PostDO> page = new Page<>(1, 10);
        page.setRecords(List.of(row(3L)));
        when(postMapper.selectPage(any(), any())).thenReturn(page);
        when(postMapper.selectCount(any())).thenReturn(2L);

        assertEquals(9L, repository.findPageByAuthorId(7L, 1, 10, " title ", "DRAFT", 3L, 4L).getFirst().getId());
        assertEquals(2L, repository.countByAuthorIdFiltered(7L, " ", "", null, null));
        assertEquals(2L, repository.countByAuthorIdFiltered(7L, null, null, null, null));
        verify(postMapper).selectPage(any(), any());
        verify(postMapper, Mockito.times(2)).selectCount(any());
    }

    private void assertHasOwnershipAndDeletionFilters(LambdaQueryWrapper<PostDO> wrapper) {
        String sql = wrapper.getSqlSegment();
        assertEquals(true, sql.contains("author_id"));
        assertEquals(true, sql.contains("status"));
        assertEquals(true, wrapper.getParamNameValuePairs().containsValue(7L));
        assertEquals(true, wrapper.getParamNameValuePairs().containsValue("DELETED"));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<PostDO>> wrapperCaptor() {
        return ArgumentCaptor.forClass((Class<LambdaQueryWrapper<PostDO>>) (Class<?>) LambdaQueryWrapper.class);
    }

    private PostDO row(Long seriesId) {
        PostDO row = new PostDO();
        row.setId(9L);
        row.setSlug("post");
        row.setTitle("标题");
        row.setContent("内容");
        row.setStatus("DRAFT");
        row.setCreatedAt(LocalDateTime.now());
        row.setUpdatedAt(LocalDateTime.now());
        row.setAuthorId(7L);
        row.setFeatured(false);
        row.setSeriesId(seriesId);
        row.setSeriesOrder(seriesId == null ? null : 2);
        return row;
    }
}
