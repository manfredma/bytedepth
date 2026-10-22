package manfred.bytedepth.adapter.web.mobile;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import manfred.bytedepth.app.interaction.MobileInteractionService;
import manfred.bytedepth.app.rating.PostRatingDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MobileRatingControllerTest {
    final MobileInteractionService service = mock(MobileInteractionService.class);
    final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders.standaloneSetup(
                    new MobileRatingController(service))
            .setControllerAdvice(new MobileExceptionHandler())
            .build();
    final String visitor = "app:7d0b5345-18ee-46b4-9609-62513f6e3043";

    @Test
    void anonymousRatingUpdatesOneInstallVote() throws Exception {
        mvc.perform(put("/api/mobile/v1/posts/1/rating")
                        .header("X-App-Visitor", visitor)
                        .contentType("application/json")
                        .content("{\"score\":3}"))
                .andExpect(status().isNoContent());
        mvc.perform(put("/api/mobile/v1/posts/1/rating")
                        .header("X-App-Visitor", visitor)
                        .contentType("application/json")
                        .content("{\"score\":5}"))
                .andExpect(status().isNoContent());
        verify(service).rate(1L, visitor, 3);
        verify(service).rate(1L, visitor, 5);
        when(service.ratings(1L, visitor)).thenReturn(new PostRatingDTO(4.5, 2, 5));
        mvc.perform(get("/api/mobile/v1/posts/1/ratings").header("X-App-Visitor", visitor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageScore").value(4.5))
                .andExpect(jsonPath("$.ratingCount").value(2))
                .andExpect(jsonPath("$.visitorScore").value(5));
    }

    @Test
    void invalidIdentityAndScoreUseTypedErrors() throws Exception {
        doThrow(new IllegalArgumentException()).when(service).rate(1L, "forged", 6);
        mvc.perform(put("/api/mobile/v1/posts/1/rating")
                        .header("X-App-Visitor", "forged")
                        .contentType("application/json")
                        .content("{\"score\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        when(service.ratings(1L, null)).thenReturn(new PostRatingDTO(0, 0, null));
        mvc.perform(get("/api/mobile/v1/posts/1/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.visitorScore").isEmpty());
    }

    @Test
    void missingInstallIdentityIsInvalidRequestRatherThanServerError() throws Exception {
        doThrow(new IllegalArgumentException()).when(service).rate(1L, null, 4);
        mvc.perform(put("/api/mobile/v1/posts/1/rating")
                        .contentType("application/json")
                        .content("{\"score\":4}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
