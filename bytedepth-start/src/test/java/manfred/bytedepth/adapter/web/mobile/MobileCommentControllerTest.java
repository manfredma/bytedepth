package manfred.bytedepth.adapter.web.mobile;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;
import manfred.bytedepth.app.interaction.MobileInteractionService;
import manfred.bytedepth.domain.comment.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobileCommentControllerTest {
    final MobileInteractionService service = mock(MobileInteractionService.class);
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MobileCommentController(service))
            .setCustomArgumentResolvers(
                    new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new MobileExceptionHandler())
            .build();

    @Test
    void approvedPageMapsSafeFieldsAndUtc() throws Exception {
        var row = Comment.reconstruct(
                9007199254740993L,
                1L,
                2L,
                "reader",
                "approved",
                CommentStatus.APPROVED,
                LocalDateTime.of(2026, 10, 1, 8, 0));
        when(service.comments(1L, 1, 10)).thenReturn(new MobileInteractionService.PageResult(List.of(row), 11));
        mvc.perform(get("/api/mobile/v1/posts/1/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].username").value("reader"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-10-01T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.hasNext").value(true));
    }

    @Test
    void invalidPageUsesJson400() throws Exception {
        mvc.perform(get("/api/mobile/v1/posts/1/comments?page=0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void submitUsesSecurityUsernameInsteadOfUntrustedBody() throws Exception {
        var principal = new manfred.bytedepth.adapter.web.security.SiteUserDetails(2L, "reader", "hash", List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext()
                .setAuthentication(
                        org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                                principal, "hash", List.of()));
        try {
            mvc.perform(post("/api/mobile/v1/posts/1/comments")
                            .contentType("application/json")
                            .content("{\"content\":\"hello\",\"username\":\"forged\"}"))
                    .andExpect(status().isNoContent());
            verify(service).comment(1L, "reader", "hello");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }
}
