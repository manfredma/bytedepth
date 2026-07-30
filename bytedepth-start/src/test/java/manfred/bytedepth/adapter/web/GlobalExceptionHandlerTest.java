package manfred.bytedepth.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rendersSafe500PageForUnexpectedExceptions() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/images/upload");

        ModelAndView result = handler.handleUnexpected(new IllegalStateException("database detail"), request);

        assertThat(result.getViewName()).isEqualTo("error/500");
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getModel()).doesNotContainValue("database detail");
    }
}
