package manfred.bytedepth.adapter.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.NoSuchElementException;

@ControllerAdvice
public class GlobalExceptionHandler {

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
}
