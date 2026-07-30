package manfred.bytedepth.adapter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rendersNotFoundPageForMissingResource() {
        ModelAndView result = handler.handleNotFound(new NoSuchElementException());

        assertThat(result.getViewName()).isEqualTo("error/404");
        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void rendersNotFoundPageForNotFoundResponseStatus() {
        ModelAndView result = handler.handleNotFound(
                new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(result.getViewName()).isEqualTo("error/404");
        assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void preservesNonNotFoundResponseStatus() {
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> handler.handleNotFound(exception)).isSameAs(exception);
    }

    @Test
    void preservesAccessDeniedForSpringSecurity() {
        AccessDeniedException exception = new AccessDeniedException("forbidden");

        assertThatThrownBy(() -> handler.handleAccessDenied(exception)).isSameAs(exception);
    }

    @Test
    void rendersSafe500PageForUnexpectedExceptions() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/images/upload");

        ModelAndView result = handler.handleUnexpected(new IllegalStateException("database detail"), request);

        assertThat(result.getViewName()).isEqualTo("error/500");
        assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(result.getModel()).doesNotContainValue("database detail");
    }
}
