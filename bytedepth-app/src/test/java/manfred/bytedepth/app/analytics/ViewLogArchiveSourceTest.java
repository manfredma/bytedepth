package manfred.bytedepth.app.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ViewLogArchiveSourceTest {

  @Test
  void sourceValuesMatchPersistedArchiveSourceNames() {
    assertEquals("post", ViewLogArchiveSource.POST.value());
    assertEquals("page", ViewLogArchiveSource.PAGE.value());
    assertEquals(2, ViewLogArchiveSource.values().length);
  }
}
