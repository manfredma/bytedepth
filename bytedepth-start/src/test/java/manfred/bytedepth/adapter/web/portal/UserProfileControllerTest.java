package manfred.bytedepth.adapter.web.portal;

import manfred.bytedepth.app.user.GetUserProfileQryExe;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.app.user.UserProfileDTO;
import manfred.bytedepth.domain.common.DomainException;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(value = UserProfileController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(ThymeleafSecurityHandlerConfig.class)
class UserProfileControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private GetUserProfileQryExe getUserProfileQryExe;

    @MockitoBean
    private VisitRequestFilter visitRequestFilter;
    @Test
    void profile_existingUser_returnsProfileView() throws Exception {
        UserProfileDTO dto = new UserProfileDTO();
        dto.setId(1L);
        dto.setUsername("alice");
        dto.setBio("Hello");
        dto.setPostCount(3);
        dto.setRecentPosts(List.of());
        when(getUserProfileQryExe.execute("alice")).thenReturn(dto);

        mockMvc.perform(get("/u/alice"))
            .andExpect(status().isOk())
            .andExpect(view().name("public/profile"))
            .andExpect(model().attribute("profile", dto));
    }

    @Test
    void profile_unknownUser_returns404() throws Exception {
        when(getUserProfileQryExe.execute("nobody"))
            .thenThrow(new DomainException("用户不存在：nobody"));

        mockMvc.perform(get("/u/nobody"))
            .andExpect(status().isNotFound());
    }
}
