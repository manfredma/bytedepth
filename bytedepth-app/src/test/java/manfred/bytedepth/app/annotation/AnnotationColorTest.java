package manfred.bytedepth.app.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnnotationColorTest {

  @Test
  void isSupported_supportedColors_returnsTrue() {
    assertThat(AnnotationColor.isSupported("red")).isTrue();
    assertThat(AnnotationColor.isSupported("yellow")).isTrue();
    assertThat(AnnotationColor.isSupported("green")).isTrue();
    assertThat(AnnotationColor.isSupported("blue")).isTrue();
  }

  @Test
  void isSupported_null_returnsFalse() {
    assertThat(AnnotationColor.isSupported(null)).isFalse();
  }

  @Test
  void isSupported_unknownColor_returnsFalse() {
    assertThat(AnnotationColor.isSupported("pink")).isFalse();
    assertThat(AnnotationColor.isSupported("")).isFalse();
  }
}
