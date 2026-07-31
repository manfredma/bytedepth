package manfred.bytedepth.infrastructure.series;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeriesDOTest {

    @Test
    void accessors_preserveEveryPersistedField() {
        SeriesDO series = new SeriesDO();
        series.setId(1L);
        series.setName("Java");
        series.setSlug("java");
        series.setDescription("基础");
        series.setAuthorId(7L);

        assertEquals(1L, series.getId());
        assertEquals("Java", series.getName());
        assertEquals("java", series.getSlug());
        assertEquals("基础", series.getDescription());
        assertEquals(7L, series.getAuthorId());
    }
}
