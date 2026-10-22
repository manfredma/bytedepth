package manfred.bytedepth.adapter.web.portal;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import manfred.bytedepth.adapter.web.security.SiteUserDetails;
import manfred.bytedepth.adapter.web.util.VisitFilterProperties;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.analytics.PostViewLogPort;
import manfred.bytedepth.app.analytics.ReadingProgressTokenPort;
import manfred.bytedepth.app.post.query.GetPostQryExe;
import manfred.bytedepth.app.reading.RecordReadingEventCmdExe;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ReadingPrivacyEndpointsTest {
    @Mock
    RecordReadingEventCmdExe command;

    @Mock
    GetPostQryExe posts;

    @Mock
    PostViewLogPort logs;

    @Mock
    ReadingProgressTokenPort tokens;

    @Spy
    VisitRequestFilter filter = new VisitRequestFilter(new VisitFilterProperties());

    @InjectMocks
    ReadingEventController events;

    @InjectMocks
    PostReadingController progress;

    MockMvc mvc;

    @BeforeEach
    void setup() {
        var user = new SiteUserDetails(7L, "reader", "", List.of());
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        mvc = MockMvcBuilders.standaloneSetup(events, progress).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"query", "header", "cookie"})
    void excludedReadingEventsAreAcknowledgedWithoutRecording(String source) throws Exception {
        var request = post("/posts/java/reading-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
          {"eventId":"00000000-0000-0000-0000-000000000001","sessionId":"00000000-0000-0000-0000-000000000002","type":"READ_OPEN","activeSecondsDelta":0,"maxScrollDepth":0,"occurredAt":"2026-10-22T00:00:00Z"}
          """);
        mark(request, source);
        mvc.perform(request).andExpect(status().isAccepted());
        verifyNoInteractions(command);
    }

    @ParameterizedTest
    @ValueSource(strings = {"query", "header", "cookie"})
    void excludedProgressCannotUseATokenFromAnEarlierNormalVisit(String source) throws Exception {
        var dto = new manfred.bytedepth.app.post.query.PostDTO();
        dto.setId(12L);
        lenient().when(posts.executeBySlug("java")).thenReturn(dto);
        lenient().when(tokens.belongsToPost("old-token", 12L)).thenReturn(true);
        var request = post("/posts/java/reading-progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        "{\"visitToken\":\"old-token\",\"activeReadSeconds\":30,\"maxScrollDepth\":80,\"completed\":true}");
        mark(request, source);
        mvc.perform(request).andExpect(status().isNoContent());
        verifyNoInteractions(posts, logs, tokens);
    }

    private void mark(MockHttpServletRequestBuilder request, String source) {
        switch (source) {
            case "query" -> request.param("bd_traffic", "verification");
            case "header" -> request.header("X-Bytedepth-Traffic", "verification");
            default -> request.cookie(new Cookie("bd_reading_mode", "private"));
        }
    }
}
