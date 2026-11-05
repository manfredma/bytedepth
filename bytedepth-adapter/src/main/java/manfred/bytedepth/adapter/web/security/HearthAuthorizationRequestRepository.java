package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** 仅在框架成功匹配 state 后标记当前回调类型，不能根据其他标签页的 Session 标志分类。 */
public final class HearthAuthorizationRequestRepository
        implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private final HttpSessionOAuth2AuthorizationRequestRepository delegate =
            new HttpSessionOAuth2AuthorizationRequestRepository();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        return delegate.loadAuthorizationRequest(request);
    }

    @Override
    public void saveAuthorizationRequest(
            OAuth2AuthorizationRequest authorization, HttpServletRequest request, HttpServletResponse response) {
        delegate.saveAuthorizationRequest(authorization, request, response);
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(
            HttpServletRequest request, HttpServletResponse response) {
        var authorization = delegate.removeAuthorizationRequest(request, response);
        if (authorization != null) {
            request.setAttribute(
                    HearthSilentAuthentication.PENDING,
                    "none".equals(authorization.getAdditionalParameters().get("prompt")));
            request.getSession().removeAttribute(HearthSilentAuthentication.INTERACTIVE);
        }
        return authorization;
    }
}
