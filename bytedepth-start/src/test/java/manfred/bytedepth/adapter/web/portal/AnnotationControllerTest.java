package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import manfred.bytedepth.app.annotation.CreateAnnotationCmdExe;
import manfred.bytedepth.app.annotation.DeleteAnnotationCmdExe;
import manfred.bytedepth.app.annotation.ListAnnotationsQryExe;
import manfred.bytedepth.app.annotation.UpdateAnnotationCmdExe;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.domain.annotation.AnnotationVisibility;
import manfred.bytedepth.domain.annotation.PostAnnotation;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.domain.post.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AnnotationController.class, excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class AnnotationControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean private RateLimitPort rateLimitPort;
    @MockitoBean private RateLimitProperties rateLimitProperties;
    @MockitoBean private PersistentTokenRepository persistentTokenRepository;
    @MockitoBean private VisitRequestFilter visitRequestFilter;
    @MockitoBean private PostRepository postRepository;
    @MockitoBean private ListAnnotationsQryExe listAnnotationsQryExe;
    @MockitoBean private CreateAnnotationCmdExe createAnnotationCmdExe;
    @MockitoBean private DeleteAnnotationCmdExe deleteAnnotationCmdExe;
    @MockitoBean private UpdateAnnotationCmdExe updateAnnotationCmdExe;
    @MockitoBean private AnnotationVisitorIdentity visitorIdentity;

    @BeforeEach void setUp() {
        when(postRepository.findBySlug("test-post")).thenReturn(Optional.of(manfred.bytedepth.domain.post.Post.reconstruct(1L, "test-post", "标题", "内容", manfred.bytedepth.domain.post.PostStatus.PUBLISHED, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), null, null, false)));
    }

    @Test void listReturnsOnlySafeDtoFields() throws Exception {
        when(visitorIdentity.existingHash(any())).thenReturn("hash");
        when(listAnnotationsQryExe.execute(1L, null, "hash")).thenReturn(List.of(annotation()));
        mockMvc.perform(get("/posts/test-post/annotations")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
                .andExpect(jsonPath("$[0].ownedByCurrentVisitor").value(false))
                .andExpect(jsonPath("$[0].userId").doesNotExist());
    }

    @Test void anonymousCreateGetsCookieIdentityAndReturnsOwnership() throws Exception {
        when(visitorIdentity.getOrCreateHash(any(), any())).thenReturn("hash");
        when(createAnnotationCmdExe.execute(eq(1L), eq(null), eq("hash"), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(anonymousAnnotation());
        mockMvc.perform(post("/posts/test-post/annotations").with(csrf()).contentType("application/json").content("{\"selectedText\":\"文本\",\"annotationText\":null,\"color\":\"yellow\",\"visibility\":\"PRIVATE\",\"startOffset\":0,\"endOffset\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownedByCurrentVisitor").value(true));
        verify(createAnnotationCmdExe).execute(eq(1L), eq(null), eq("hash"), eq("文本"), eq(null), eq("yellow"), eq(AnnotationVisibility.PRIVATE), eq(0), eq(2));
    }

    @Test void invalidCreateReturnsBadRequest() throws Exception {
        when(visitorIdentity.getOrCreateHash(any(), any())).thenReturn("hash");
        doThrow(new DomainException("批注偏移越界")).when(createAnnotationCmdExe).execute(any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt());
        mockMvc.perform(post("/posts/test-post/annotations").with(csrf()).contentType("application/json").content("{\"selectedText\":\"文本\",\"color\":\"yellow\",\"visibility\":\"PRIVATE\",\"startOffset\":2,\"endOffset\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test void anonymousDeleteAndPatchUseExistingIdentity() throws Exception {
        when(visitorIdentity.existingHash(any())).thenReturn("hash");
        when(updateAnnotationCmdExe.execute(10L, 1L, null, "hash", "新评论", AnnotationVisibility.PRIVATE)).thenReturn(anonymousAnnotation());
        mockMvc.perform(delete("/posts/test-post/annotations/10").with(csrf())).andExpect(status().isNoContent());
        verify(deleteAnnotationCmdExe).execute(10L, 1L, null, "hash");
        mockMvc.perform(patch("/posts/test-post/annotations/10").with(csrf()).contentType("application/json").content("{\"annotationText\":\"新评论\",\"visibility\":\"PRIVATE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    private static PostAnnotation annotation() { return new PostAnnotation(10L, 1L, 42L, null, "文本", "评论", "yellow", AnnotationVisibility.PUBLIC, 0, 2, LocalDateTime.now(), false); }
    private static PostAnnotation anonymousAnnotation() { return new PostAnnotation(10L, 1L, null, "hash", "文本", null, "yellow", AnnotationVisibility.PRIVATE, 0, 2, LocalDateTime.now(), false); }
}
