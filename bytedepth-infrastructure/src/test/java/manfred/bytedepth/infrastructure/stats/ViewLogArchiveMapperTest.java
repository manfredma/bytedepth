package manfred.bytedepth.infrastructure.stats;

import manfred.bytedepth.app.analytics.ViewLogArchiveSource;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViewLogArchiveMapperTest {

    @Test
    void defaultAggregateMethodsCombineHourlyAndCountryWrites() {
        var mapper = mock(ViewLogArchiveMapper.class, CALLS_REAL_METHODS);
        var start = LocalDateTime.of(2026, 9, 11, 17, 0);
        var end = start.plusHours(1);
        when(mapper.insertExactHourlyAggregates(ViewLogArchiveSource.POST, start, end)).thenReturn(2);
        when(mapper.insertExactCountryAggregates(ViewLogArchiveSource.POST, start, end)).thenReturn(3);
        when(mapper.incrementHourlyAggregates(ViewLogArchiveSource.POST, start, end)).thenReturn(5);
        when(mapper.incrementCountryAggregates(ViewLogArchiveSource.POST, start, end)).thenReturn(7);

        assertEquals(5, mapper.insertExactAggregates(ViewLogArchiveSource.POST, start, end));
        assertEquals(12, mapper.incrementAggregates(ViewLogArchiveSource.POST, start, end));
    }
}
