package manfred.bytedepth.adapter.web.mobile;

import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.app.annotation.AnnotationWriteConflictException;
import manfred.bytedepth.app.annotation.InvalidAnchorException;
import manfred.bytedepth.app.user.UsernameTakenException;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "manfred.bytedepth.adapter.web.mobile")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class MobileExceptionHandler {
    @ExceptionHandler(UsernameTakenException.class)
    public ResponseEntity<MobileApiError> usernameTaken(UsernameTakenException ex) {
        return error(409, "USERNAME_TAKEN", "用户名已存在");
    }

    @ExceptionHandler({
        DomainException.class,
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        MethodArgumentNotValidException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    public ResponseEntity<MobileApiError> invalid(Exception ex) {
        return error(400, "INVALID_REQUEST", "请求参数无效");
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<MobileApiError> missing(NoSuchElementException ex) {
        return error(404, "POST_UNAVAILABLE", "内容不可用");
    }

    @ExceptionHandler(InvalidAnchorException.class)
    public ResponseEntity<MobileApiError> anchor(InvalidAnchorException ex) {
        return error(422, "INVALID_ANCHOR", "批注位置无效");
    }

    @ExceptionHandler(AnnotationWriteConflictException.class)
    public ResponseEntity<MobileApiError> conflict(AnnotationWriteConflictException ex) {
        return error(
                ex.kind() == AnnotationWriteConflictException.Kind.DELETED_REQUEST ? 410 : 409,
                ex.kind().name(),
                "批注版本冲突");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<MobileApiError> denied(AccessDeniedException ex) {
        return error(403, "FORBIDDEN", "请求被拒绝");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MobileApiError> unexpected(Exception ex) {
        log.error("Unexpected mobile API failure", ex);
        return error(500, "INTERNAL_ERROR", "请求失败");
    }

    private ResponseEntity<MobileApiError> error(int status, String code, String message) {
        return ResponseEntity.status(status)
                .header("Cache-Control", "no-store")
                .body(new MobileApiError(code, message));
    }
}
