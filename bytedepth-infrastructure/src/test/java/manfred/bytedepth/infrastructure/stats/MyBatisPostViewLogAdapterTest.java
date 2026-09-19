package manfred.bytedepth.infrastructure.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.LocalDateTime;
import manfred.bytedepth.app.analytics.PostViewLogDTO;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MyBatisPostViewLogAdapterTest {

    @Test
    void delegatesAllLogOperationsToMapper() {
        PostViewLogMapper mapper = Mockito.mock(PostViewLogMapper.class);
        MyBatisPostViewLogAdapter adapter = new MyBatisPostViewLogAdapter(mapper);
        List<PostViewLogDTO> logs = List.of(new PostViewLogDTO());
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 11, 17, 23);
        when(mapper.findPage(1L, 2L, cutoff, 3, 4)).thenReturn(logs);
        when(mapper.countPage(1L, 2L, cutoff)).thenReturn(5L);

        adapter.upsertReadingProgress(1L, "token", 6, 7, true);

        assertEquals(logs, adapter.findPage(1L, 2L, cutoff, 3, 4));
        assertEquals(5L, adapter.countPage(1L, 2L, cutoff));
        verify(mapper).upsertReadingProgress(1L, "token", 6, 7, true);
    }
}
