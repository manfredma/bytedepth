package manfred.bytedepth.adapter.web.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** 授权材料仍由框架生成，只给经过 CSRF 启动的后台请求追加静默参数。 */
public final class HearthAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {
    private final DefaultOAuth2AuthorizationRequestResolver delegate;
    private final HearthSilentAuthentication silent = new HearthSilentAuthentication();

    public HearthAuthorizationRequestResolver(ClientRegistrationRepository registrations) {
        delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        return customize(request, delegate.resolve(request));
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String registrationId) {
        return customize(request, delegate.resolve(request, registrationId));
    }

    private OAuth2AuthorizationRequest customize(HttpServletRequest request, OAuth2AuthorizationRequest original) {
        if (original == null) {
            return null;
        }
        if ("true".equals(request.getParameter("silent"))) {
            if (!silent.arm(request)) {
                return null;
            }
            return OAuth2AuthorizationRequest.from(original)
                    .additionalParameters(parameters -> parameters.put("prompt", "none"))
                    .build();
        }
        // 用户主动登录必须替换旧静默上下文，不能复用失败请求的 state/nonce/verifier。
        silent.interactiveStarted(request);
        return original;
    }
}
