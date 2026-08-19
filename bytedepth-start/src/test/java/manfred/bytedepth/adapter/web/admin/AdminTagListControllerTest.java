package manfred.bytedepth.adapter.web.admin;

import manfred.bytedepth.app.tag.DeleteTagCmdExe;
import manfred.bytedepth.adapter.web.security.SecurityConfig;
import manfred.bytedepth.adapter.web.ratelimit.RateLimitProperties;
import manfred.bytedepth.app.ratelimit.RateLimitPort;
import manfred.bytedepth.adapter.web.security.SecurityMockMvcConfig;
import manfred.bytedepth.adapter.web.security.ThymeleafSecurityHandlerConfig;
import manfred.bytedepth.app.tag.ListTagsQryExe;
import manfred.bytedepth.adapter.web.util.VisitRequestFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(value = AdminTagListController.class,
        excludeAutoConfiguration = DataSourceAutoConfiguration.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class})
@Import({SecurityConfig.class, ThymeleafSecurityHandlerConfig.class, SecurityMockMvcConfig.class})
class AdminTagListControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private PasswordEncoder passwordEncoder;
    @MockitoBean
    private VisitRequestFilter visitRequestFilter;
    @MockitoBean
    private RateLimitPort rateLimitPort;
    @MockitoBean
    private RateLimitProperties rateLimitProperties;
    @MockitoBean private ListTagsQryExe listTagsQryExe;
    @MockitoBean private DeleteTagCmdExe deleteTagCmdExe;

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void list_populatesTagsModel() throws Exception {
        when(listTagsQryExe.findPageWithCount(null, 1, 20)).thenReturn(new ListTagsQryExe.TagPageResult(java.util.List.of(), 0));

        mockMvc.perform(get("/admin/tags"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/tags/list"))
                .andExpect(model().attribute("tags", java.util.List.of()));
    }

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void list_forwardsFilterAndPagination() throws Exception {
        when(listTagsQryExe.findPageWithCount("Java", 2, 10)).thenReturn(new ListTagsQryExe.TagPageResult(java.util.List.of(), 11));

        mockMvc.perform(get("/admin/tags").param("name", "Java").param("page", "2").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBaseUrl", "/admin/tags?name=Java&"))
                .andExpect(model().attribute("totalPages", 2));

        verify(listTagsQryExe).findPageWithCount("Java", 2, 10);
    }

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void list_blankNameDoesNotAddItToPaginationUrl() throws Exception {
        when(listTagsQryExe.findPageWithCount(" ", 1, 20)).thenReturn(new ListTagsQryExe.TagPageResult(java.util.List.of(), 0));

        mockMvc.perform(get("/admin/tags").param("name", " "))
                .andExpect(status().isOk())
                .andExpect(model().attribute("filterBaseUrl", "/admin/tags?"));
    }

    @Test
    @WithMockUser(authorities = "admin:dashboard:view")
    void delete_delegatesAndReturnsToTagList() throws Exception {
        mockMvc.perform(post("/admin/tags/3/delete").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/tags"));

        verify(deleteTagCmdExe).execute(3L);
    }
}
