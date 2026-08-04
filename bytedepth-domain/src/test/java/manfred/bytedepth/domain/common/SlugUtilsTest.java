package manfred.bytedepth.domain.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlugUtilsTest {

    @Test
    void slugify_coversBlankShortNormalAndTruncatedInput() {
        assertEquals("", SlugUtils.slugify(null));
        assertEquals("", SlugUtils.slugify("  "));
        assertEquals("", SlugUtils.slugify("a!"));
        assertEquals("java-21-guide", SlugUtils.slugify("Java 21 Guide!"));
        assertEquals("a".repeat(80), SlugUtils.slugify("a".repeat(81)));
    }

    @Test
    void isValid_coversAllFormatRules() {
        assertFalse(SlugUtils.isValid(null));
        assertFalse(SlugUtils.isValid(" "));
        assertFalse(SlugUtils.isValid("-start"));
        assertFalse(SlugUtils.isValid("two--dashes"));
        assertTrue(SlugUtils.isValid("a"));
        assertTrue(SlugUtils.isValid("java-21"));
    }
}
