package manfred.bytedepth.adapter.web.util;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

class CsrfTokenInitializerTest {

  @Test
  void initializesDeferredCsrfTokenBeforeTemplateRendering() {
    HttpServletRequest request = mock(HttpServletRequest.class);
    CsrfToken token = mock(CsrfToken.class);
    when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);

    CsrfTokenInitializer.initialize(request);

    verify(token).getToken();
  }
}
