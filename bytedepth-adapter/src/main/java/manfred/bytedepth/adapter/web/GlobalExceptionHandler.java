package manfred.bytedepth.adapter.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.domain.common.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.NoSuchElementException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /** 领域校验失败（如批注偏移越界、超长文本、无权删除）→ 400 BadRequest */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<String> handleDomain(DomainException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    /** 文章不存在 or 已删除 → 404 页面 */
    @ExceptionHandler({NoSuchElementException.class, ResponseStatusException.class})
    public ModelAndView handleNotFound(Exception ex) {
        if (ex instanceof ResponseStatusException rse
                && rse.getStatusCode() != HttpStatus.NOT_FOUND) {
            // 非 404 的 ResponseStatusException 不在这里处理，继续向上抛
            throw rse;
        }
        ModelAndView mav = new ModelAndView("error/404");
        mav.setStatus(HttpStatus.NOT_FOUND);
        return mav;
    }

    /** 授权失败交回 Spring Security 的 AccessDeniedHandler，保持原有 403 响应。 */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) {
        throw ex;
    }

    /** 未预期异常 → 不泄露内部信息，记录完整日志并展示站内 500 页面。 */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request failure: method={}, path={}", request.getMethod(), request.getRequestURI(), ex);
        ModelAndView mav = new ModelAndView("error/500");
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mav;
    }
}
