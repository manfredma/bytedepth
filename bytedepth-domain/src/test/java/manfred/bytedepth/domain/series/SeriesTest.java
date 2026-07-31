package manfred.bytedepth.domain.series;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SeriesTest {

    @Test
    void createWithAuthor_preservesAllFields() {
        Series series = Series.create("Java", "java", "基础知识", 7L);

        assertNull(series.getId());
        assertEquals("Java", series.getName());
        assertEquals("java", series.getSlug());
        assertEquals("基础知识", series.getDescription());
        assertEquals(7L, series.getAuthorId());
    }

    @Test
    void legacyFactories_keepAuthorUnset() {
        Series created = Series.create("Java", "java", null);
        Series reconstructed = Series.reconstruct(3L, "Java", "java", null);

        assertNull(created.getAuthorId());
        assertEquals(3L, reconstructed.getId());
        assertNull(reconstructed.getAuthorId());
    }

    @Test
    void reconstructWithAuthor_preservesPersistedIdentity() {
        Series series = Series.reconstruct(3L, "Java", "java", "desc", 8L);

        assertEquals(3L, series.getId());
        assertEquals("Java", series.getName());
        assertEquals("java", series.getSlug());
        assertEquals("desc", series.getDescription());
        assertEquals(8L, series.getAuthorId());
    }
}
