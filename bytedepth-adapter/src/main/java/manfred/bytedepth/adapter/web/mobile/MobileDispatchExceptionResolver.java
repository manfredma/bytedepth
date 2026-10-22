package manfred.bytedepth.adapter.web.mobile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import manfred.bytedepth.adapter.web.security.RouteSessionIdResolver;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.handler.AbstractHandlerExceptionResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Framework failures can occur before a mobile controller has been selected. */
@Component
@Slf4j
public class MobileDispatchExceptionResolver extends AbstractHandlerExceptionResolver {
    public MobileDispatchExceptionResolver() {
        // Package-scoped ControllerAdvice cannot own resource handlers or failed route selection.
        setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    protected ModelAndView doResolveException(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        if (!RouteSessionIdResolver.isMobileRequest(request)) return null;
        int status;
        String code;
        if (exception instanceof HttpMediaTypeNotSupportedException) {
            status = 415;
            code = "UNSUPPORTED_MEDIA_TYPE";
        } else if (exception instanceof HttpMediaTypeNotAcceptableException) {
            status = 406;
            code = "NOT_ACCEPTABLE";
        } else if (exception instanceof HttpRequestMethodNotSupportedException unsupported) {
            if (unsupported.getSupportedMethods() != null) {
                response.setHeader("Allow", String.join(", ", unsupported.getSupportedMethods()));
            }
            status = 405;
            code = "METHOD_NOT_ALLOWED";
        } else if (exception instanceof NoResourceFoundException || exception instanceof NoHandlerFoundException) {
            status = 404;
            code = "NOT_FOUND";
        } else {
            return null;
        }
        try {
            MobileApiError.write(response, status, code, "请求无法处理");
        } catch (IOException ex) {
            // A disconnected client cannot receive a fallback HTML error; keep the failure in server logs.
            log.error("Unable to write mobile dispatch error", ex);
        }
        return new ModelAndView();
    }
}
