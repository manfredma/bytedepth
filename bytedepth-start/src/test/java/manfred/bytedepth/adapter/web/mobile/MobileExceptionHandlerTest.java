package manfred.bytedepth.adapter.web.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import manfred.bytedepth.app.annotation.AnnotationWriteConflictException;
import manfred.bytedepth.app.annotation.InvalidAnchorException;
import manfred.bytedepth.app.user.UsernameTakenException;
import manfred.bytedepth.domain.annotation.PlacementReason;
import manfred.bytedepth.domain.common.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class MobileExceptionHandlerTest {
    private final MobileExceptionHandler handler = new MobileExceptionHandler();

    @Test
    void domainAndAuthorizationFailuresHaveStableStatusCodesAndNoInternalDetails() {
        assertError(handler.usernameTaken(new UsernameTakenException()), 409, "USERNAME_TAKEN");
        assertError(handler.invalid(new DomainException("secret")), 400, "INVALID_REQUEST");
        assertError(handler.missing(new java.util.NoSuchElementException("secret")), 404, "POST_UNAVAILABLE");
        assertError(handler.anchor(new InvalidAnchorException(PlacementReason.values()[0])), 422, "INVALID_ANCHOR");
        assertError(handler.denied(new AccessDeniedException("secret")), 403, "FORBIDDEN");
        for (var kind : AnnotationWriteConflictException.Kind.values()) {
            assertError(
                    handler.conflict(new AnnotationWriteConflictException(kind)),
                    kind == AnnotationWriteConflictException.Kind.DELETED_REQUEST ? 410 : 409,
                    kind.name());
        }
    }

    @Test
    void unexpectedFailureIsLoggedAndNeverExposesInternalException() {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(MobileExceptionHandler.class);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        boolean additive = logger.isAdditive();
        logger.setAdditive(false);
        logger.addAppender(events);
        events.start();
        try {
            assertError(handler.unexpected(new IllegalStateException("secret")), 500, "INTERNAL_ERROR");
            assertThat(events.list)
                    .singleElement()
                    .satisfies(event -> assertThat(event.getThrowableProxy()).isNotNull());
        } finally {
            logger.detachAppender(events);
            logger.setAdditive(additive);
        }
    }

    private void assertError(
            org.springframework.http.ResponseEntity<MobileApiError> response, int status, String code) {
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().code()).isEqualTo(code);
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
