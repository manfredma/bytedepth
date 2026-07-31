package manfred.bytedepth.infrastructure.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import manfred.bytedepth.app.analytics.PostViewLogDTO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MyBatisPostViewLogAdapterTest {

    @Test
    void delegatesAllLogOperationsToMapper() {
        PostViewLogMapper mapper = Mockito.mock(PostViewLogMapper.class);
        MyBatisPostViewLogAdapter adapter = new MyBatisPostViewLogAdapter(mapper);
        List<PostViewLogDTO> logs = List.of(new PostViewLogDTO());
        when(mapper.findPage(1L, 2L, 3, 4)).thenReturn(logs);
        when(mapper.countPage(1L, 2L)).thenReturn(5L);

        adapter.upsertReadingProgress(1L, "token", 6, 7, true);

        assertEquals(logs, adapter.findPage(1L, 2L, 3, 4));
        assertEquals(5L, adapter.countPage(1L, 2L));
        verify(mapper).upsertReadingProgress(1L, "token", 6, 7, true);
    }
}
