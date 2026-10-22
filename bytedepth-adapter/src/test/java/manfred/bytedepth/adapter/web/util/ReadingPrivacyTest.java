package manfred.bytedepth.adapter.web.util;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

class ReadingPrivacyTest {
    private final ReadingPrivacy privacy = new ReadingPrivacy();

    @Test
    void pageEventsUseTheSamePolicyAndOrdinaryTrafficStillProducesAnEvent() throws Exception {
        var captured = new java.util.ArrayList<Object>();
        var filter = new VisitRequestFilter(new VisitFilterProperties());
        var interceptor = new PageViewInterceptor(filter, captured::add);
        var handler = new org.springframework.web.method.HandlerMethod(
                this, getClass().getDeclaredMethod("pageHandler"));
        var request = new MockHttpServletRequest("GET", "/about");
        request.setParameter("bd_traffic", "verification");
        interceptor.preHandle(request, new MockHttpServletResponse(), handler);
        assertThat(captured).isEmpty();
        request = new MockHttpServletRequest("GET", "/about");
        interceptor.preHandle(request, new MockHttpServletResponse(), handler);
        assertThat(captured).hasSize(1);
        assertThat(captured.getFirst()).isInstanceOf(manfred.bytedepth.domain.stats.PageViewedEvent.class);
    }

    @SuppressWarnings("unused")
    private void pageHandler() {}

    @Test
    void ordinaryAndUnknownMarkersDoNotDisableRecording() {
        var request = new MockHttpServletRequest();
        assertThat(ReadingPrivacy.isExcluded(request)).isFalse();
        request.setParameter("bd_traffic", "unknown");
        request.addHeader("X-Bytedepth-Traffic", "unknown");
        request.setCookies(new Cookie("other", "private"), new Cookie("bd_reading_mode", "unknown"));
        var response = new MockHttpServletResponse();
        assertThat(privacy.preHandle(request, response, new Object())).isTrue();
        assertThat(ReadingPrivacy.isExcluded(request)).isFalse();
        assertThat(response.getHeader("Set-Cookie")).isNull();
    }

    @Test
    void verificationEntryExcludesTheFirstRequestAndSubsequentCookieRequests() {
        var request = new MockHttpServletRequest("GET", "/posts/42");
        request.setSecure(true);
        request.setParameter("bd_traffic", "verification");
        var response = new MockHttpServletResponse();
        privacy.preHandle(request, response, new Object());
        assertThat(ReadingPrivacy.isExcluded(request)).isTrue();
        assertThat(response.getHeader("Set-Cookie"))
                .contains("bd_reading_mode=verification", "Max-Age=1800", "Path=/", "Secure", "SameSite=Lax");
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        var next = new MockHttpServletRequest();
        next.setCookies(new Cookie("bd_reading_mode", "verification"));
        assertThat(ReadingPrivacy.isExcluded(next)).isTrue();
        var nextResponse = new MockHttpServletResponse();
        privacy.preHandle(next, nextResponse, new Object());
        assertThat(nextResponse.getHeader("Set-Cookie")).isNull();
        var model = new ModelAndView("public/reading-history");
        privacy.postHandle(next, nextResponse, new Object(), model);
        assertThat(model.getModel()).containsEntry("privateReading", true);
        privacy.postHandle(next, nextResponse, new Object(), null);
        var redirect = new ModelAndView("redirect:/posts/java");
        privacy.postHandle(next, nextResponse, new Object(), redirect);
        assertThat(redirect.getModel()).isEmpty();
    }

    @Test
    void manualPreferenceIsASessionCookieAndVerificationDoesNotShortenIt() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        ReadingPrivacy.setPreference(request, response, true);
        assertThat(response.getHeader("Set-Cookie"))
                .contains("bd_reading_mode=private")
                .doesNotContain("Max-Age", "Expires", "Secure;");
        request.setCookies(new Cookie("bd_reading_mode", "private"));
        request.setParameter("bd_traffic", "verification");
        var verificationResponse = new MockHttpServletResponse();
        privacy.preHandle(request, verificationResponse, new Object());
        assertThat(verificationResponse.getHeader("Set-Cookie")).isNull();
        assertThat(ReadingPrivacy.isPrivate(request)).isTrue();
    }

    @Test
    void exitClearsTheCookieAndRestoresThisRequestButCannotOverrideAnExplicitHeader() {
        var request = new MockHttpServletRequest();
        request.setCookies(new Cookie("bd_reading_mode", "verification"));
        request.setParameter("bd_traffic", "normal");
        var response = new MockHttpServletResponse();
        privacy.preHandle(request, response, new Object());
        assertThat(response.getHeader("Set-Cookie")).contains("bd_reading_mode=", "Max-Age=0");
        assertThat(ReadingPrivacy.isExcluded(request)).isFalse();
        request.addHeader("X-Bytedepth-Traffic", "verification");
        assertThat(ReadingPrivacy.isExcluded(request)).isTrue();
    }

    @Test
    void explicitPrivacyIsHonoredEvenWhenUaRulesAreDisabledAndUaFallbackStillWorks() {
        var properties = new VisitFilterProperties();
        properties.setEnabled(false);
        var filter = new VisitRequestFilter(properties);
        var request = new MockHttpServletRequest();
        request.addHeader("X-Bytedepth-Traffic", "verification");
        assertThat(filter.shouldRecord(VisitRequestFilter.Request.from(request)))
                .isFalse();
        var rule = new VisitFilterProperties.Rule();
        rule.setId("obsidian");
        rule.setField(VisitFilterProperties.Field.USER_AGENT);
        rule.setMatch(VisitFilterProperties.Match.CONTAINS);
        rule.setValue("obsidian");
        properties.setEnabled(true);
        properties.setRules(List.of(rule));
        var ordinary = new MockHttpServletRequest();
        ordinary.addHeader("User-Agent", "Mozilla/5.0 Obsidian/1.0");
        assertThat(filter.shouldRecord(VisitRequestFilter.Request.from(ordinary)))
                .isFalse();
        ordinary = new MockHttpServletRequest();
        ordinary.addHeader("User-Agent", "Mozilla/5.0");
        assertThat(filter.shouldRecord(VisitRequestFilter.Request.from(ordinary)))
                .isTrue();
        var ordinaryModel = new ModelAndView("public/reading-history");
        privacy.postHandle(ordinary, new MockHttpServletResponse(), new Object(), ordinaryModel);
        assertThat(ordinaryModel.getModel()).containsEntry("privateReading", false);
    }
}
